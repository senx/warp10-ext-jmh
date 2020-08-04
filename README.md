# JMH - Java Microbenchmark Harness

## Introduction 

This extension allow running benchmarks using [JMH](https://openjdk.java.net/projects/code-tools/jmh/) in WarpScript.

For instance, if you want to benchmark the `COS` function, you would run the following script:
```
// If you do not need pre/post macros,
// you can just push a macro instead of a map.
{
  'preinvocation'  <% RAND %> 
  'macro'          <% COS %>
  'postinvocation' <% DROP %>
}
// All the keys are optional in the following map
{ 
  'mem' '64m' 
  'forks' 1 
  'warmupTime' '1 s'
  'measurementTime' '1 s'
  'timeUnit' 'MICROSECONDS'
}
// Run the benchmark. Be careful, it is usually quite long.
JMH
```

## Benchmarked Macro and Setup/TearDown Macros

The given macro is the only code being benchmarked. If you need to configure the stack, store or push element on it without impacting the benchmark, you need to do that in [Setup/TearDown](http://tutorials.jenkov.com/java-performance/jmh.html#state-setup-and-teardown) macros associated to the following keys:
- pretrial
- preiteration
- preinvocation
- postinvocation
- postiteration
- posttrial

If `postinvocation` is not set, it defaults to `<% CLEAR %>`.

## Optional Configuration

Valid keys are:
- `forks`
- `measurementBatchSize`
- `measurementIterations`
- `measurementTime`
- `mode`
- `operationsPerInvocation`
- `profilers`
- `shouldDoGC`
- `syncIterations`
- `threads` cannot be used as stack are mono-threaded
- `timeUnit`
- `warmupBatchSize`
- `warmupForks`
- `warmupIterations`
- `warmupMode`
- `warmupTime`
- `mem` set memory using both -Xms and -Xmx