/*
 * Sonar Cryptography Plugin
 * Copyright (C) 2024 PQCA
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.output.cyclonedx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.ibm.mapper.model.Algorithm;
import com.ibm.mapper.model.INode;
import com.ibm.mapper.model.Key;
import com.ibm.mapper.utils.DetectionLocation;
import com.ibm.output.cyclondx.CBOMOutputFile;
import java.util.List;
import java.util.Map;
import org.cyclonedx.Version;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.model.Bom;
import org.junit.jupiter.api.Test;

class CBOMOutputFileRegressionTest {

    @Test
    void goldenFileRegressionTest() throws Exception {
        // 1. AST Setup: First occurrence of AES with a dependent Key
        // This validates parent bom-ref linkage and evidence assembly
        DetectionLocation loc1 = mockLocation("src/A.java", 10, 5, "AES");
        Algorithm aes1 = mockNode(Algorithm.class, "AES", loc1);

        DetectionLocation locKey = mockLocation("src/A.java", 11, 2, "Key");
        Key key1 = mockNode(Key.class, "SecretKey", locKey);
        doReturn(Map.of(Key.class, key1)).when(aes1).getChildren();
        doReturn(true).when(aes1).hasChildren();

        // 2. AST Setup: Second occurrence of AES (in a different file)
        // This validates component deduplication and occurrence counting
        DetectionLocation loc2 = mockLocation("src/B.java", 20, 2, "AES");
        Algorithm aes2 = mockNode(Algorithm.class, "AES", loc2);
        doReturn(Map.of()).when(aes2).getChildren();

        // 3. Process the nodes using the new refactored pipeline
        CBOMOutputFile cbomOutputFile = new CBOMOutputFile();
        cbomOutputFile.add((Iterable<INode>) List.<INode>of(aes1, aes2));
        Bom bom = cbomOutputFile.getBom();

        // 4. Normalize dynamic fields (timestamps and versions)
        bom.getMetadata().setTimestamp(null);
        if (bom.getMetadata().getToolChoice() != null
                && !bom.getMetadata().getToolChoice().getServices().isEmpty()) {
            bom.getMetadata().getToolChoice().getServices().get(0).setVersion("1.0.0");
        }

        // 5. Generate JSON output
        String jsonOutput = BomGeneratorFactory.createJson(Version.VERSION_16, bom).toJsonString();

        // Regex replace ALL UUIDs (including random bom-refs, serialNumbers, and generated names)
        // with STATIC-UUID
        // This guarantees the test is 100% deterministic on every run.
        jsonOutput =
                jsonOutput.replaceAll(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                        "STATIC-UUID");

        // 6. Assert against Golden File String representing the actual plugin schema
        String expectedGoldenJson =
                """
        {
          "bomFormat" : "CycloneDX",
          "specVersion" : "1.6",
          "serialNumber" : "urn:uuid:STATIC-UUID",
          "version" : 1,
          "metadata" : {
            "tools" : {
              "services" : [
                {
                  "provider" : {
                    "name" : "IBM"
                  },
                  "name" : "SonarCryptographyPlugin",
                  "version" : "1.0.0"
                }
              ]
            }
          },
          "components" : [
            {
              "type" : "cryptographic-asset",
              "bom-ref" : "STATIC-UUID",
              "name" : "key@STATIC-UUID",
              "evidence" : {
                "occurrences" : [
                  {
                    "location" : "src/A.java",
                    "line" : 11,
                    "offset" : 2,
                    "additionalContext" : "Key"
                  }
                ]
              },
              "cryptoProperties" : {
                "assetType" : "related-crypto-material",
                "relatedCryptoMaterialProperties" : { }
              }
            },
            {
              "type" : "cryptographic-asset",
              "bom-ref" : "STATIC-UUID",
              "name" : "AES",
              "evidence" : {
                "occurrences" : [
                  {
                    "location" : "src/A.java",
                    "line" : 10,
                    "offset" : 5,
                    "additionalContext" : "AES"
                  },
                  {
                    "location" : "src/B.java",
                    "line" : 20,
                    "offset" : 2,
                    "additionalContext" : "AES"
                  }
                ]
              },
              "cryptoProperties" : {
                "assetType" : "algorithm",
                "algorithmProperties" : {
                  "primitive" : "other"
                }
              }
            }
          ],
          "dependencies" : [
            {
              "ref" : "STATIC-UUID",
              "dependsOn" : [
                "STATIC-UUID"
              ]
            }
          ]
        }
        """;

        // Strip whitespaces to ensure formatting changes don't fail the functional equivalence
        // check
        assertThat(jsonOutput.replaceAll("\\s+", ""))
                .isEqualTo(expectedGoldenJson.replaceAll("\\s+", ""));
    }

    private <T extends INode> T mockNode(Class<T> clazz, String name, DetectionLocation loc) {
        T mock = mock(clazz);
        doReturn(name).when(mock).asString();
        doReturn(clazz).when(mock).getKind();
        doReturn(false).when(mock).hasChildren();

        if (Algorithm.class.isAssignableFrom(clazz)) {
            doReturn(loc).when((Algorithm) mock).getDetectionContext();
        } else if (Key.class.isAssignableFrom(clazz)) {
            doReturn(loc).when((Key) mock).getDetectionContext();
        }

        return mock;
    }

    private DetectionLocation mockLocation(String filePath, int line, int offset, String keyword) {
        DetectionLocation loc = mock(DetectionLocation.class);
        doReturn(filePath).when(loc).filePath();
        doReturn(line).when(loc).lineNumber();
        doReturn(offset).when(loc).offSet();
        doReturn(List.of(keyword)).when(loc).keywords();
        return loc;
    }
}
