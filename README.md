# TeamCity Agent State Plugin [![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

The plugin stores the current agent state into file `<TC agent directory>/system/agent-state/current-state`.
The file has follwoing format: `<number>|<state name>`:
- `0|shutdown`
- `1|starting`
- `2|idle`
- `3|preparing`
- `4|working`
