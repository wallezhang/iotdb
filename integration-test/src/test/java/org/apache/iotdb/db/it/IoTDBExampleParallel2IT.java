/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.it;

import org.apache.iotdb.it.env.EnvFactory;
import org.apache.iotdb.itbase.category.ClusterIT;
import org.apache.iotdb.itbase.category.LocalStandaloneIT;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** This is an example for integration test. */
@Category({LocalStandaloneIT.class, ClusterIT.class})
public class IoTDBExampleParallel2IT {
  @Before
  public void setUp() throws Exception {
    EnvFactory.getEnv().initBeforeTest();
  }

  @After
  public void tearDown() throws Exception {
    EnvFactory.getEnv().cleanAfterTest();
  }

  @Test
  public void exampleTest() throws Exception {
    try (Connection connection = EnvFactory.getEnv().getConnection();
        Statement statement = connection.createStatement()) {

      statement.execute("set storage group to root.sg");
      statement.executeQuery("show storage group");
      ResultSet resultSet = statement.getResultSet();
      if (resultSet.next()) {
        String storageGroupPath = resultSet.getString("storage group");
        Assert.assertEquals("root.sg", storageGroupPath);
      } else {
        Assert.fail("This ResultSet is empty.");
      }
    }
  }

  @Test
  public void exampleTest1() throws Exception {
    try (Connection connection = EnvFactory.getEnv().getConnection();
        Statement statement = connection.createStatement()) {

      statement.execute("set storage group to root.sg");
      statement.executeQuery("show storage group");
      ResultSet resultSet = statement.getResultSet();
      if (resultSet.next()) {
        String storageGroupPath = resultSet.getString("storage group");
        Assert.assertEquals("root.sg", storageGroupPath);
      } else {
        Assert.fail("This ResultSet is empty.");
      }
    }
  }

  @Test
  public void exampleTest2() throws Exception {
    try (Connection connection = EnvFactory.getEnv().getConnection();
        Statement statement = connection.createStatement()) {

      statement.execute("set storage group to root.sg");
      statement.executeQuery("show storage group");
      ResultSet resultSet = statement.getResultSet();
      if (resultSet.next()) {
        String storageGroupPath = resultSet.getString("storage group");
        Assert.assertEquals("root.sg", storageGroupPath);
      } else {
        Assert.fail("This ResultSet is empty.");
      }
    }
  }
}
