/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.mpp.plan.planner.plan.node.metedata.read;

import org.apache.iotdb.db.mpp.plan.planner.plan.node.PlanNode;
import org.apache.iotdb.db.mpp.plan.planner.plan.node.PlanNodeId;
import org.apache.iotdb.db.mpp.plan.planner.plan.node.PlanNodeType;
import org.apache.iotdb.db.mpp.plan.planner.plan.node.PlanVisitor;
import org.apache.iotdb.db.mpp.plan.planner.plan.node.process.MultiChildNode;

import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.List;

public class SchemaQueryOrderByHeatNode extends MultiChildNode {

  public SchemaQueryOrderByHeatNode(PlanNodeId id) {
    super(id);
  }

  /** show timeseries */
  private PlanNode left;

  /** last point */
  private PlanNode right;

  public PlanNode getLeft() {
    return left;
  }

  public PlanNode getRight() {
    return right;
  }

  @Override
  public List<PlanNode> getChildren() {
    return ImmutableList.of(left, right);
  }

  @Override
  public void addChild(PlanNode child) {
    if (child instanceof SchemaQueryMergeNode) {
      left = child;
    } else {
      right = child;
    }
  }

  @Override
  public PlanNode clone() {
    return new SchemaQueryOrderByHeatNode(getPlanNodeId());
  }

  @Override
  public int allowedChildCount() {
    return CHILD_COUNT_NO_LIMIT;
  }

  @Override
  public List<String> getOutputColumnNames() {
    return left.getOutputColumnNames();
  }

  @Override
  public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
    return visitor.visitSchemaQueryOrderByHeat(this, context);
  }

  @Override
  protected void serializeAttributes(ByteBuffer byteBuffer) {
    PlanNodeType.SCHEMA_QUERY_ORDER_BY_HEAT.serialize(byteBuffer);
  }

  public static SchemaQueryOrderByHeatNode deserialize(ByteBuffer byteBuffer) {
    PlanNodeId planNodeId = PlanNodeId.deserialize(byteBuffer);
    return new SchemaQueryOrderByHeatNode(planNodeId);
  }

  public String toString() {
    return String.format("SchemaQueryOrderByHeatNode-%s", getPlanNodeId());
  }
}
