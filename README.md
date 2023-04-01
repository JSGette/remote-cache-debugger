# Bazel Remote Cache Debugger

This is a simple tool to compare 2 execution logs and scrape some cache hit metrics.

## Features

* Compare two execution logs and collect the list of environment variables and inputs
  that are different across executions. Right now the tool doesn't compare command_args and other
  attributes of [SpawnExec](https://github.com/JSGette/remote-cache-debugger/blame/main/src/main/proto/spawn.proto#L67)
* Consumes execution logs in binary format that have been produced directly by bazel. So no need to use any 
other tools to transform them beforehand.
* Produces output logs in both text and binary format so that the result can be consumed by other
  applications/tools based on [output.proto](src/main/proto/output.proto)

## How to use
To see all supported commands just use --help/-h flag:
</br>`java -jar remote-cache-debugger.jar -h`

To compare 2 execution logs:
</br>`java -jar remote-cache-debugger.jar -first <path_to_exec1.log> -second <path_to_exec2.log>`

To compare execution logs and generate a text report:
</br>`java -jar remote-cache-debugger.jar -first <path_to_exec1.log> -second <path_to_exec2.log> -o <path_to_text_output>`

To compare execution logs and generate a binary report:
</br>`java -jar remote-cache-debugger.jar -first <path_to_exec1.log> -second <path_to_exec2.log> -ob <path_to_binary_output>`

## Limitations
*Hopefully, these limitations will be solved soon enough*
* The tool doesn't compare all attributes of SpawnExec as mentioned above
* If you built another target or changed the flags/options/features of the build most probably
results will be opaque
* If sequential execution log contains more inputs/environment variables the tool won't track it
