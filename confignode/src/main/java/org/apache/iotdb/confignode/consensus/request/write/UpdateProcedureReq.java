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

package org.apache.iotdb.confignode.consensus.request.write;

import org.apache.iotdb.confignode.consensus.request.ConfigRequest;
import org.apache.iotdb.confignode.consensus.request.ConfigRequestType;
import org.apache.iotdb.confignode.procedure.Procedure;
import org.apache.iotdb.confignode.procedure.store.ProcedureFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class UpdateProcedureReq extends ConfigRequest {

  private Procedure procedure;

  public Procedure getProcedure() {
    return procedure;
  }

  public void setProcedure(Procedure procedure) {
    this.procedure = procedure;
  }

  public UpdateProcedureReq() {
    super(ConfigRequestType.UpdateProcedure);
  }

  @Override
  protected void serializeImpl(ByteBuffer buffer) {
    buffer.putInt(ConfigRequestType.UpdateProcedure.ordinal());
    if (procedure != null) {
      procedure.serialize(buffer);
    }
  }

  @Override
  protected void deserializeImpl(ByteBuffer buffer) throws IOException {
    Procedure procedure = ProcedureFactory.getInstance().create(buffer);
    this.procedure = procedure;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UpdateProcedureReq that = (UpdateProcedureReq) o;
    return Objects.equals(procedure, that.procedure);
  }

  @Override
  public int hashCode() {
    return Objects.hash(procedure);
  }
}
