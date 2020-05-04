package org.jetbrains.teamcity.agentState;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class AgentStateListener extends AgentLifeCycleAdapter {
    private final StateUpdater myStateUpdater;

    public AgentStateListener(@NotNull final EventDispatcher<AgentLifeCycleListener> dispatcher, @NotNull final BuildAgentConfiguration config) {
        dispatcher.addListener(this);
        myStateUpdater = new StateUpdater(config, State.STARTING);
    }

    @Override
    public void agentStarted(@NotNull final BuildAgent agent) {
        myStateUpdater.set(State.IDLE);
    }

    @Override
    public void agentShutdown() {
        myStateUpdater.set(State.SHUTDOWN);
        myStateUpdater.shutdown();
    }

    @Override
    public void buildStarted(@NotNull final AgentRunningBuild runningBuild) {
        myStateUpdater.set(State.PREPARING);
    }

    @Override
    public void preparationFinished(@NotNull final AgentRunningBuild runningBuild) {
        myStateUpdater.set(State.WORKING);
    }

    @Override
    public void buildFinished(@NotNull final AgentRunningBuild build, @NotNull final BuildFinishedStatus buildStatus) {
        myStateUpdater.set(State.IDLE);
    }

    private enum State {
        SHUTDOWN("0|shutdown"),
        STARTING("1|starting"),
        IDLE("2|idle"),
        PREPARING("3|preparing"),
        WORKING("4|working");

        private final String myValue;

        State(@NotNull final String value) {
            myValue = value;
        }

        @NotNull
        public String getValue() {
            return myValue;
        }
    }

    private static final class StateUpdater {
        private final static Logger LOG = Logger.getInstance("jetbrains.buildServer.AGENT_STATE");
        private final Thread myThread;
        private boolean myIsStopRequest = false;
        private State myState;

        public StateUpdater(@NotNull final BuildAgentConfiguration config, @NotNull final State state) {
            myState = state;
            final File file = new File(config.getCacheDirectory("agent-state"), "current-state");
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (State prevState = null; ; ) {
                        State currState;
                        synchronized (myThread) {
                            currState = myState;
                            if (prevState == currState) {
                                if (myIsStopRequest)
                                    break;
                                try {
                                    myThread.wait();
                                } catch (InterruptedException ie) {
                                }
                                continue;
                            }
                        }

                        try {
                            FileUtil.writeFileAndReportErrors(file, currState.getValue());
                        } catch (IOException e) {
                            LOG.warn(String.format("Failed to update currState to %s, try again", currState.name()));
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ie) {
                            }
                            continue;
                        }

                        prevState = currState;
                        LOG.info(String.format("State was updated to %s", currState.name()));
                    }
                    LOG.info("The update thread was exited");
                }
            });
            thread.setName("AgentStateUpdater");
            thread.setDaemon(true);
            thread.start();
            myThread = thread;
        }

        public void set(@NotNull final State state) {
            LOG.info(String.format("The state change to %s was requested", state.name()));
            synchronized (myThread) {
                myState = state;
                myThread.notify();
            }
        }

        public void shutdown() {
            LOG.info("The update thread exit was requested");
            synchronized (myThread) {
                myIsStopRequest = true;
                myThread.notify();
            }
            try {
                myThread.join();
            } catch (InterruptedException ie) {
                LOG.warn("The thread join was interrupted");
            }
        }
    }
}
