//
//    Copyright 2020  SenX S.A.S.
//
//    Licensed under the Apache License, Version 2.0 (the "License");
//    you may not use this file except in compliance with the License.
//    You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//    Unless required by applicable law or agreed to in writing, software
//    distributed under the License is distributed on an "AS IS" BASIS,
//    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//    See the License for the specific language governing permissions and
//    limitations under the License.
//

package io.warp10.script.ext.jmh;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.warp10.WarpConfig;
import io.warp10.continuum.Configuration;
import io.warp10.json.JsonUtils;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatFactory;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;
import org.openjdk.jmh.runner.options.WarmupMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class JMH extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public static final String JVM_ARG_PREFIX = "-Djmh.macrosnapshot.file=";

  public JMH(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    // Get parameters override Map
    Object top = stack.pop();

    if (!(top instanceof Map)) {
      throw new WarpScriptException(getName() + "expects a Map of JHM parameters.");
    }

    Map<Object, Object> parameters = (Map) top;

    // Get Macro to benchmark
    top = stack.pop();

    if (!(top instanceof WarpScriptStack.Macro)) {
      throw new WarpScriptException(getName() + "expects a Macro.");
    }

    String macroSnapshot = ((WarpScriptStack.Macro) top).snapshot();
    File temp = null;

    try {
      // Write the snapshot to a temporary file. We must use a temp file because passing the snapshot as a JVM arg
      // may fail if it's too long.
      temp = File.createTempFile("macrosnapshot", ".mc2");
      try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
        writer.write(macroSnapshot);
      }

      ArrayList<String> jvmArgsAppend = new ArrayList<>();
      jvmArgsAppend.add(JVM_ARG_PREFIX + temp.getAbsolutePath());

      // Build base options
      ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
          // Include only MacroBenchmark. Do not use class.getName() to avoid cyclic dependency.
          .include("io\\.warp10\\.script\\.ext\\.jmh\\.MacroBenchmark.*")
          // Remove output on stdout
          .verbosity(VerboseMode.SILENT)
          // Throw instead of returning empty result
          .shouldFailOnError(true);

      // Override parameters with those given
      overrideParameters(optionsBuilder, jvmArgsAppend, parameters);

      // Copy warpscript extensions properties
      Properties props = WarpConfig.getProperties();
      for (String key: props.stringPropertyNames()) {
        if (key.equals(Configuration.CONFIG_WARPSCRIPT_EXTENSIONS) || key.startsWith(Configuration.CONFIG_WARPSCRIPT_EXTENSION_PREFIX)) {
          jvmArgsAppend.add("-D" + key + "=" + props.getProperty(key));
        }
      }

      optionsBuilder.jvmArgsAppend(jvmArgsAppend.toArray(new String[jvmArgsAppend.size()]));

      // Run the JMH Benchmark
      Collection<RunResult> runResults = new Runner(optionsBuilder.build()).run();

      // Write results
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final String utf8 = StandardCharsets.UTF_8.name();
      try (PrintStream ps = new PrintStream(baos, true, utf8)) {
        ResultFormatFactory.getInstance(ResultFormatType.JSON, ps).writeOut(runResults);
        String json = baos.toString(utf8);
        stack.push(JsonUtils.jsonToObject(json));
      } catch (UnsupportedEncodingException e) {
        // cannot happen
      } catch (JsonProcessingException e) {
        throw new WarpScriptException(getName() + " failed because the JMH JSON is invalid.", e);
      }
    } catch (RunnerException e) {
      throw new WarpScriptException(getName() + " failed.", e);
    } catch (IOException e) {
      throw new WarpScriptException(getName() + " could not create temporary file.", e);
    } finally {
      if (null != temp) {
        temp.delete();
      }
    }

    return stack;
  }

  private void overrideParameters(ChainedOptionsBuilder optionsBuilder, List<String> jvmArgs, Map<Object, Object> parameters) throws WarpScriptException {

    for (Map.Entry<Object, Object> entry: parameters.entrySet()) {
      if (!(entry.getKey() instanceof String)) {
        throw new WarpScriptException(getName() + " expects parameter keys to be String.");
      }

      switch ((String) entry.getKey()) {
        case "forks":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects forks to be a Long.");
          }
          try {
            optionsBuilder.forks(Math.toIntExact((Long) entry.getValue()));
          } catch (ArithmeticException | IllegalArgumentException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "measurementBatchSize":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects measurementBatchSize to be a Long.");
          }
          try {
            optionsBuilder.measurementBatchSize(Math.toIntExact((Long) entry.getValue()));
          } catch (ArithmeticException | IllegalArgumentException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "measurementIterations":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects measurementIterations to be a Long.");
          }
          try {
            optionsBuilder.measurementIterations(Math.toIntExact((Long) entry.getValue()));
          } catch (ArithmeticException | IllegalArgumentException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "measurementTime":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects measurementTime to be a Long.");
          }
          try {
            optionsBuilder.measurementTime(TimeValue.valueOf((String) entry.getValue()));
          } catch (IllegalArgumentException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "mode":
          if (!(entry.getValue() instanceof String)) {
            throw new WarpScriptException(getName() + " expects mode to be a String.");
          }
          try {
            optionsBuilder.mode(Mode.deepValueOf((String) entry.getValue()));
          } catch (IllegalArgumentException iae) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", iae);
          }
          break;
        case "operationsPerInvocation":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects operationsPerInvocation to be a Long.");
          }
          try {
            optionsBuilder.operationsPerInvocation(Math.toIntExact((Long) entry.getValue()));
          } catch (IllegalArgumentException | ArithmeticException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "profilers":
          if (!(entry.getValue() instanceof List)) {
            throw new WarpScriptException(getName() + " expects profilers to be a List.");
          }
          try {
            for (Object o: (List) entry.getValue()) {
              if (!(o instanceof String)) {
                throw new WarpScriptException(getName() + " expects profilers to be a List of String.");
              }
              optionsBuilder.addProfiler((String) o);
            }
          } catch (IllegalArgumentException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "shouldDoGC":
          if (!(entry.getValue() instanceof Boolean)) {
            throw new WarpScriptException(getName() + " expects shouldDoGC to be a Boolean.");
          }
          optionsBuilder.shouldDoGC((Boolean) entry.getValue());
          break;
        case "syncIterations":
          if (!(entry.getValue() instanceof Boolean)) {
            throw new WarpScriptException(getName() + " expects syncIterations to be a Long.");
          }
          try {
            optionsBuilder.syncIterations((Boolean) entry.getValue());
          } catch (IllegalArgumentException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "threads":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects threads to be a Long.");
          }
          try {
            optionsBuilder.threads(Math.toIntExact((Long) entry.getValue()));
          } catch (ArithmeticException | IllegalArgumentException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "timeUnit":
          if (!(entry.getValue() instanceof String)) {
            throw new WarpScriptException(getName() + " expects timeUnit to be a String.");
          }
          try {
            optionsBuilder.timeUnit(TimeUnit.valueOf((String) entry.getValue()));
          } catch (IllegalArgumentException iae) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", iae);
          }
          break;
        case "warmupBatchSize":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects warmupBatchSize to be a Long.");
          }
          try {
            optionsBuilder.warmupBatchSize(Math.toIntExact((Long) entry.getValue()));
          } catch (IllegalArgumentException | ArithmeticException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "warmupForks":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects warmupForks to be a Long.");
          }
          try {
            optionsBuilder.warmupForks(Math.toIntExact((Long) entry.getValue()));
          } catch (IllegalArgumentException | ArithmeticException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "warmupIterations":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects warmupIterations to be a Long.");
          }
          try {
            optionsBuilder.warmupIterations(Math.toIntExact((Long) entry.getValue()));
          } catch (IllegalArgumentException | ArithmeticException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "warmupMode":
          if (!(entry.getValue() instanceof String)) {
            throw new WarpScriptException(getName() + " expects warmupMode to be a String.");
          }
          try {
            optionsBuilder.warmupMode(WarmupMode.valueOf((String) entry.getValue()));
          } catch (IllegalArgumentException iae) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", iae);
          }
          break;
        case "warmupTime":
          if (!(entry.getValue() instanceof Long)) {
            throw new WarpScriptException(getName() + " expects warmupTime to be a Long.");
          }
          try {
            optionsBuilder.warmupTime(TimeValue.valueOf((String) entry.getValue()));
          } catch (IllegalArgumentException e) {
            throw new WarpScriptException(getName() + " expects valid parameter option.", e);
          }
          break;
        case "mem":
          if (!(entry.getValue() instanceof String)) {
            throw new WarpScriptException(getName() + " expects mem to be a String.");
          }
          jvmArgs.add("-Xms" + (String) entry.getValue());
          jvmArgs.add("-Xmx" + (String) entry.getValue());
          break;
        default:
          throw new WarpScriptException(getName() + " expects parameter keys to be in [forks, measurementBatchSize, measurementIterations, measurementTime, mode, operationsPerInvocation, profilers, shouldDoGC, syncIterations, threads, timeUnit, warmupBatchSize, warmupForks, warmupIterations, warmupMode, warmupTime, mem]");
      }
    }
  }

}
