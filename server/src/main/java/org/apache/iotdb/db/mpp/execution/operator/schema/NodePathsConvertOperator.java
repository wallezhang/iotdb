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

package org.apache.iotdb.db.mpp.execution.operator.schema;

import org.apache.iotdb.commons.exception.IllegalPathException;
import org.apache.iotdb.commons.path.PartialPath;
import org.apache.iotdb.db.mpp.common.header.HeaderConstant;
import org.apache.iotdb.db.mpp.execution.operator.Operator;
import org.apache.iotdb.db.mpp.execution.operator.OperatorContext;
import org.apache.iotdb.db.mpp.execution.operator.process.ProcessOperator;
import org.apache.iotdb.tsfile.read.common.block.TsBlock;
import org.apache.iotdb.tsfile.read.common.block.TsBlockBuilder;
import org.apache.iotdb.tsfile.utils.Binary;

import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class NodePathsConvertOperator implements ProcessOperator {

  private static final Logger LOGGER = LoggerFactory.getLogger(NodePathsConvertOperator.class);

  private final OperatorContext operatorContext;
  private final Operator child;
  private boolean isFinished;

  public NodePathsConvertOperator(OperatorContext operatorContext, Operator child) {
    this.operatorContext = requireNonNull(operatorContext, "operatorContext is null");
    this.child = requireNonNull(child, "child operator is null");
    isFinished = false;
  }

  @Override
  public OperatorContext getOperatorContext() {
    return operatorContext;
  }

  @Override
  public ListenableFuture<Void> isBlocked() {
    return child.isBlocked();
  }

  @Override
  public TsBlock next() {
    isFinished = true;
    TsBlock block = child.next();
    TsBlockBuilder tsBlockBuilder =
        new TsBlockBuilder(HeaderConstant.showChildNodesHeader.getRespDataTypes());

    for (int i = 0; i < block.getPositionCount(); i++) {
      String path = block.getColumn(0).getBinary(i).toString();
      PartialPath partialPath;
      try {
        partialPath = new PartialPath(path);
      } catch (IllegalPathException e) {
        LOGGER.warn("Failed to convert node path to PartialPath {}", path);
        continue;
      }
      tsBlockBuilder.getTimeColumnBuilder().writeLong(0L);
      tsBlockBuilder.getColumnBuilder(0).writeBinary(new Binary(partialPath.getTailNode()));
      tsBlockBuilder.declarePosition();
    }

    return tsBlockBuilder.build();
  }

  @Override
  public boolean hasNext() {
    return child.hasNext();
  }

  @Override
  public void close() throws Exception {
    child.close();
  }

  @Override
  public boolean isFinished() {
    return isFinished || child.isFinished();
  }
}
