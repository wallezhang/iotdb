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
package org.apache.iotdb.db.mpp.plan.planner.plan.node.process;

import org.apache.iotdb.db.mpp.plan.planner.plan.node.PlanNode;
import org.apache.iotdb.db.mpp.plan.planner.plan.node.PlanNodeId;
import org.apache.iotdb.db.mpp.plan.planner.plan.node.PlanNodeType;
import org.apache.iotdb.db.mpp.plan.planner.plan.node.PlanVisitor;
import org.apache.iotdb.db.mpp.plan.planner.plan.parameter.AggregationDescriptor;
import org.apache.iotdb.db.mpp.plan.planner.plan.parameter.GroupByLevelDescriptor;
import org.apache.iotdb.db.mpp.plan.planner.plan.parameter.GroupByTimeParameter;
import org.apache.iotdb.db.mpp.plan.statement.component.OrderBy;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;

import javax.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This node is responsible for the final aggregation merge operation. It will process the data from
 * TsBlock row by row. For one row, it will rollup the fields which have the same aggregate function
 * and belong to one bucket. Here, that two columns belong to one bucket means the partial paths of
 * device after rolling up in specific level are the same.
 *
 * <p>For example, let's say there are two columns `root.sg.d1.s1` and `root.sg.d2.s1`.
 *
 * <p>If the group by level parameter is [0, 1], then these two columns will belong to one bucket
 * and the bucket name is `root.sg.*.s1`.
 *
 * <p>If the group by level parameter is [0, 2], then these two columns will not belong to one
 * bucket. And the total buckets are `root.*.d1.s1` and `root.*.d2.s1`
 */
public class GroupByLevelNode extends MultiChildNode {

  // The list of aggregate descriptors
  // each GroupByLevelDescriptor will be output as one or two column of result TsBlock
  protected List<GroupByLevelDescriptor> groupByLevelDescriptors;

  // The parameter of `group by time`.
  // Its value will be null if there is no `group by time` clause.
  @Nullable protected GroupByTimeParameter groupByTimeParameter;

  protected OrderBy scanOrder;

  public GroupByLevelNode(
      PlanNodeId id,
      List<PlanNode> children,
      List<GroupByLevelDescriptor> groupByLevelDescriptors,
      GroupByTimeParameter groupByTimeParameter,
      OrderBy scanOrder) {
    super(id, children);
    this.groupByLevelDescriptors = groupByLevelDescriptors;
    this.groupByTimeParameter = groupByTimeParameter;
    this.scanOrder = scanOrder;
  }

  public GroupByLevelNode(
      PlanNodeId id,
      List<GroupByLevelDescriptor> groupByLevelDescriptors,
      GroupByTimeParameter groupByTimeParameter,
      OrderBy scanOrder) {
    super(id);
    this.groupByLevelDescriptors = groupByLevelDescriptors;
    this.groupByTimeParameter = groupByTimeParameter;
    this.scanOrder = scanOrder;
  }

  @Override
  public List<PlanNode> getChildren() {
    return children;
  }

  @Override
  public void addChild(PlanNode child) {
    this.children.add(child);
  }

  @Override
  public int allowedChildCount() {
    return CHILD_COUNT_NO_LIMIT;
  }

  @Override
  public PlanNode clone() {
    return new GroupByLevelNode(
        getPlanNodeId(), getGroupByLevelDescriptors(), this.groupByTimeParameter, this.scanOrder);
  }

  public List<GroupByLevelDescriptor> getGroupByLevelDescriptors() {
    return groupByLevelDescriptors;
  }

  public void setGroupByLevelDescriptors(List<GroupByLevelDescriptor> groupByLevelDescriptors) {
    this.groupByLevelDescriptors = groupByLevelDescriptors;
  }

  @Override
  public List<String> getOutputColumnNames() {
    return groupByLevelDescriptors.stream()
        .map(AggregationDescriptor::getOutputColumnNames)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  @Override
  public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
    return visitor.visitGroupByLevel(this, context);
  }

  @Override
  protected void serializeAttributes(ByteBuffer byteBuffer) {
    PlanNodeType.GROUP_BY_LEVEL.serialize(byteBuffer);
    ReadWriteIOUtils.write(groupByLevelDescriptors.size(), byteBuffer);
    for (GroupByLevelDescriptor groupByLevelDescriptor : groupByLevelDescriptors) {
      groupByLevelDescriptor.serialize(byteBuffer);
    }
    if (groupByTimeParameter == null) {
      ReadWriteIOUtils.write((byte) 0, byteBuffer);
    } else {
      ReadWriteIOUtils.write((byte) 1, byteBuffer);
      groupByTimeParameter.serialize(byteBuffer);
    }
    ReadWriteIOUtils.write(scanOrder.ordinal(), byteBuffer);
  }

  public static GroupByLevelNode deserialize(ByteBuffer byteBuffer) {
    int descriptorSize = ReadWriteIOUtils.readInt(byteBuffer);
    List<GroupByLevelDescriptor> groupByLevelDescriptors = new ArrayList<>();
    while (descriptorSize > 0) {
      groupByLevelDescriptors.add(GroupByLevelDescriptor.deserialize(byteBuffer));
      descriptorSize--;
    }
    byte isNull = ReadWriteIOUtils.readByte(byteBuffer);
    GroupByTimeParameter groupByTimeParameter = null;
    if (isNull == 1) {
      groupByTimeParameter = GroupByTimeParameter.deserialize(byteBuffer);
    }
    OrderBy scanOrder = OrderBy.values()[ReadWriteIOUtils.readInt(byteBuffer)];
    PlanNodeId planNodeId = PlanNodeId.deserialize(byteBuffer);
    return new GroupByLevelNode(
        planNodeId, groupByLevelDescriptors, groupByTimeParameter, scanOrder);
  }

  @Nullable
  public GroupByTimeParameter getGroupByTimeParameter() {
    return groupByTimeParameter;
  }

  public OrderBy getScanOrder() {
    return scanOrder;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    GroupByLevelNode that = (GroupByLevelNode) o;
    return Objects.equals(groupByLevelDescriptors, that.groupByLevelDescriptors)
        && Objects.equals(groupByTimeParameter, that.groupByTimeParameter)
        && scanOrder == that.scanOrder;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), groupByLevelDescriptors, groupByTimeParameter, scanOrder);
  }

  public String toString() {
    return String.format(
        "GroupByLevelNode-%s: Output: %s, Input: %s",
        getPlanNodeId(), getOutputColumnNames(), groupByLevelDescriptors.size());
  }
}
