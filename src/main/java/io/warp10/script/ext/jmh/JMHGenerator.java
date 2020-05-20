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

import org.openjdk.jmh.generators.core.BenchmarkGenerator;
import org.openjdk.jmh.generators.core.FileSystemDestination;
import org.openjdk.jmh.generators.core.SourceError;
import org.openjdk.jmh.generators.reflection.RFGeneratorSource;

import java.io.File;

public class JMHGenerator {

  public static void main(String[] args) {
    String sourcesDir = args[0];
    String resourcesDir = args[1];

    // Clean old generated sources and resources
    deleteDirectory(new File(sourcesDir));
    deleteDirectory(new File(resourcesDir));

    RFGeneratorSource source = new RFGeneratorSource();
    source.processClasses(MacroBenchmark.class);

    File resources = new File(resourcesDir);
    File sources = new File(sourcesDir);
    FileSystemDestination destination = new FileSystemDestination(resources, sources);

    BenchmarkGenerator gen = new BenchmarkGenerator();
    gen.generate(source, destination);
    gen.complete(source, destination);

    if (destination.hasErrors()) {
      StringBuilder sb = new StringBuilder();
      for (SourceError e: destination.getErrors()) {
        sb.append("  - ").append(e.toString()).append("\n");
      }
      throw new RuntimeException("JMH generator failed: " + sb.toString());
    }
  }

  private static boolean deleteDirectory(File directoryToBeDeleted) {
    File[] allContents = directoryToBeDeleted.listFiles();
    if (allContents != null) {
      for (File file: allContents) {
        deleteDirectory(file);
      }
    }
    return directoryToBeDeleted.delete();
  }

}
