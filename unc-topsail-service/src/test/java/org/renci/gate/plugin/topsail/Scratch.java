package org.renci.gate.plugin.topsail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.renci.gate.GlideinMetric;
import org.renci.jlrm.Queue;
import org.renci.jlrm.Site;
import org.renci.jlrm.slurm.SLURMJobStatusInfo;
import org.renci.jlrm.slurm.ssh.SLURMSSHLookupStatusCallable;

public class Scratch {

    @Test
    public void testLookupMetrics() {

        Map<String, GlideinMetric> metricsMap = new HashMap<String, GlideinMetric>();

        Site site = new Site();
        site.setName("Topsail");
        site.setProject("TCGA");
        site.setUsername("pipeline");
        site.setSubmitHost("topsail-sn.unc.edu");
        site.setMaxTotalPending(4);
        site.setMaxTotalRunning(4);

        Map<String, Queue> queueInfoMap = new HashMap<String, Queue>();

        Queue queue = new Queue();
        queue.setMaxJobLimit(10);
        queue.setMaxMultipleJobsToSubmit(2);
        queue.setName("queue16");
        queue.setWeight(1D);
        queue.setPendingTime(1440);
        queue.setRunTime(5760);
        queueInfoMap.put("queue16", queue);
        site.setQueueInfoMap(queueInfoMap);

        try {

            SLURMSSHLookupStatusCallable callable = new SLURMSSHLookupStatusCallable(site);
            Set<SLURMJobStatusInfo> jobStatusSet = Executors.newSingleThreadExecutor().submit(callable).get();

            // get unique list of queues
            Set<String> queueSet = new HashSet<String>();
            if (jobStatusSet != null && jobStatusSet.size() > 0) {
                for (SLURMJobStatusInfo info : jobStatusSet) {
                    queueSet.add(info.getQueue());
                }

                for (SLURMJobStatusInfo info : jobStatusSet) {
                    if (metricsMap.containsKey(info.getQueue())) {
                        continue;
                    }
                    if (!"glidein".equals(info.getJobName())) {
                        continue;
                    }
                    metricsMap.put(info.getQueue(), new GlideinMetric(0, 0, info.getQueue()));
                }

                for (SLURMJobStatusInfo info : jobStatusSet) {

                    if (!"glidein".equals(info.getJobName())) {
                        continue;
                    }

                    switch (info.getType()) {
                        case PENDING:
                            metricsMap.get(info.getQueue()).incrementPending();
                            break;
                        case RUNNING:
                            metricsMap.get(info.getQueue()).incrementRunning();
                            break;
                    }
                }
            }

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        for (String key : metricsMap.keySet()) {
            GlideinMetric metric = metricsMap.get(key);
            System.out.println(metric.toString());
        }

    }

}
