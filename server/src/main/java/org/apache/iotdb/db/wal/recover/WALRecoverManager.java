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
package org.apache.iotdb.db.wal.recover;

import org.apache.iotdb.commons.concurrent.IoTDBThreadPoolFactory;
import org.apache.iotdb.commons.concurrent.ThreadName;
import org.apache.iotdb.commons.file.SystemFileFactory;
import org.apache.iotdb.commons.utils.TestOnly;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.DataRegionException;
import org.apache.iotdb.db.wal.exception.WALRecoverException;
import org.apache.iotdb.db.wal.recover.file.UnsealedTsFileRecoverPerformer;
import org.apache.iotdb.db.wal.utils.listener.WALRecoverListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/** First set allVsgScannedLatch, then call recover method. */
public class WALRecoverManager {
  private static final Logger logger = LoggerFactory.getLogger(WALRecoverManager.class);
  private static final IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();

  /** start recovery after all virtual storage groups have submitted unsealed zero-level TsFiles */
  private volatile CountDownLatch allDataRegionScannedLatch;
  /** threads to recover wal nodes */
  private ExecutorService recoverThreadPool;
  /** stores all UnsealedTsFileRecoverPerformer submitted by virtual storage group processors */
  private final Map<String, UnsealedTsFileRecoverPerformer> absolutePath2RecoverPerformer =
      new ConcurrentHashMap<>();

  private WALRecoverManager() {}

  public void recover() throws WALRecoverException {
    logger.info("Start recovering wal.");
    try {
      // collect wal nodes' information
      List<File> walNodeDirs = new ArrayList<>();
      for (String walDir : config.getWalDirs()) {
        File walDirFile = SystemFileFactory.INSTANCE.getFile(walDir);
        File[] nodeDirs = walDirFile.listFiles(File::isDirectory);
        if (nodeDirs == null) {
          continue;
        }
        for (File nodeDir : nodeDirs) {
          if (nodeDir.isDirectory()) {
            walNodeDirs.add(nodeDir);
          }
        }
      }
      // wait until all virtual storage groups have submitted their unsealed TsFiles,
      // which means walRecoverManger.addRecoverPerformer method won't be call anymore
      try {
        allDataRegionScannedLatch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new WALRecoverException("Fail to recover wal.", e);
      }
      logger.info(
          "Data regions have submitted all unsealed TsFiles, start recovering TsFiles in each wal node.");
      // recover each wal node's TsFiles
      if (!walNodeDirs.isEmpty()) {
        recoverThreadPool =
            IoTDBThreadPoolFactory.newCachedThreadPool(ThreadName.WAL_RECOVER.getName());
        CountDownLatch allNodesRecoveredLatch = new CountDownLatch(walNodeDirs.size());
        for (File walNodeDir : walNodeDirs) {
          recoverThreadPool.submit(new WALNodeRecoverTask(walNodeDir, allNodesRecoveredLatch));
        }

        try {
          allNodesRecoveredLatch.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new WALRecoverException("Fail to recover wal.", e);
        }
      }
      // deal with remaining TsFiles which don't have wal
      for (UnsealedTsFileRecoverPerformer recoverPerformer :
          absolutePath2RecoverPerformer.values()) {
        try {
          recoverPerformer.startRecovery();
          // skip redo logs because it doesn't belong to any wal node
          recoverPerformer.endRecovery();
          recoverPerformer.getRecoverListener().succeed();
        } catch (DataRegionException | IOException e) {
          logger.error(
              "Fail to recover unsealed TsFile {}, skip it.",
              recoverPerformer.getTsFileAbsolutePath(),
              e);
          recoverPerformer.getRecoverListener().fail(e);
        }
      }
    } catch (Exception e) {
      for (UnsealedTsFileRecoverPerformer recoverPerformer :
          absolutePath2RecoverPerformer.values()) {
        recoverPerformer.getRecoverListener().fail(e);
      }
    } finally {
      for (UnsealedTsFileRecoverPerformer recoverPerformer :
          absolutePath2RecoverPerformer.values()) {
        try {
          if (!recoverPerformer.canWrite()) {
            recoverPerformer.close();
          }
        } catch (Exception e) {
          // continue
        }
      }
      clear();
    }
    logger.info("Successfully recover all wal nodes.");
  }

  public WALRecoverListener addRecoverPerformer(UnsealedTsFileRecoverPerformer recoverPerformer) {
    absolutePath2RecoverPerformer.put(recoverPerformer.getTsFileAbsolutePath(), recoverPerformer);
    return recoverPerformer.getRecoverListener();
  }

  UnsealedTsFileRecoverPerformer removeRecoverPerformer(String absolutePath) {
    return absolutePath2RecoverPerformer.remove(absolutePath);
  }

  public CountDownLatch getAllDataRegionScannedLatch() {
    return allDataRegionScannedLatch;
  }

  public void setAllDataRegionScannedLatch(CountDownLatch allDataRegionScannedLatch) {
    this.allDataRegionScannedLatch = allDataRegionScannedLatch;
  }

  @TestOnly
  public void clear() {
    absolutePath2RecoverPerformer.clear();
    if (recoverThreadPool != null) {
      recoverThreadPool.shutdown();
      recoverThreadPool = null;
    }
  }

  public static WALRecoverManager getInstance() {
    return InstanceHolder.INSTANCE;
  }

  private static class InstanceHolder {
    private InstanceHolder() {}

    private static final WALRecoverManager INSTANCE = new WALRecoverManager();
  }
}
