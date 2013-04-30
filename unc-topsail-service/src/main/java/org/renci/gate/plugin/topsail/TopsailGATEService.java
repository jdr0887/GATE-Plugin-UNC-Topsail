package org.renci.gate.plugin.topsail;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.renci.gate.AbstractGATEService;
import org.renci.gate.GlideinMetric;
import org.renci.jlrm.Queue;
import org.renci.jlrm.slurm.SLURMJobStatusInfo;
import org.renci.jlrm.slurm.SLURMJobStatusType;
import org.renci.jlrm.slurm.ssh.SLURMSSHJob;
import org.renci.jlrm.slurm.ssh.SLURMSSHKillCallable;
import org.renci.jlrm.slurm.ssh.SLURMSSHLookupStatusCallable;
import org.renci.jlrm.slurm.ssh.SLURMSSHSubmitCondorGlideinCallable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author jdr0887
 */
public class TopsailGATEService extends AbstractGATEService {

    private final Logger logger = LoggerFactory.getLogger(TopsailGATEService.class);

    private final List<SLURMSSHJob> jobCache = new ArrayList<SLURMSSHJob>();

    public TopsailGATEService() {
        super();
    }

    @Override
    public Map<String, GlideinMetric> lookupMetrics() {
        logger.info("ENTERING lookupMetrics()");
        Map<String, GlideinMetric> metricsMap = new HashMap<String, GlideinMetric>();

        try {
            SLURMSSHLookupStatusCallable callable = new SLURMSSHLookupStatusCallable(getSite(), jobCache);
            Set<SLURMJobStatusInfo> jobStatusSet = Executors.newSingleThreadExecutor().submit(callable).get();
            logger.debug("jobStatusSet.size(): {}", jobStatusSet.size());

            // get unique list of queues
            Set<String> queueSet = new HashSet<String>();
            if (jobStatusSet != null && jobStatusSet.size() > 0) {
                for (SLURMJobStatusInfo info : jobStatusSet) {
                    queueSet.add(info.getQueue());
                }
                for (SLURMSSHJob job : jobCache) {
                    queueSet.add(job.getQueueName());
                }
            }

            Set<String> alreadyTalliedJobIdSet = new HashSet<String>();

            if (jobStatusSet != null && jobStatusSet.size() > 0) {
                for (SLURMJobStatusInfo info : jobStatusSet) {
                    if (metricsMap.containsKey(info.getQueue())) {
                        continue;
                    }
                    if (!"glidein".equals(info.getJobName())) {
                        continue;
                    }
                    metricsMap.put(info.getQueue(), new GlideinMetric(0, 0, info.getQueue()));
                    alreadyTalliedJobIdSet.add(info.getJobId());
                }

                for (SLURMJobStatusInfo info : jobStatusSet) {

                    if (!"glidein".equals(info.getJobName())) {
                        continue;
                    }

                    GlideinMetric metric = metricsMap.get(info.getQueue());
                    switch (info.getType()) {
                        case PENDING:
                            metric.setPending(metric.getPending() + 1);
                            break;
                        case RUNNING:
                            metric.setRunning(metric.getRunning() + 1);
                            break;
                    }
                    logger.debug("metric: {}", metric.toString());
                }
            }

            Iterator<SLURMSSHJob> jobCacheIter = jobCache.iterator();
            while (jobCacheIter.hasNext()) {
                SLURMSSHJob nextJob = jobCacheIter.next();
                for (SLURMJobStatusInfo info : jobStatusSet) {

                    if (!nextJob.getName().equals(info.getJobName())) {
                        continue;
                    }

                    if (!alreadyTalliedJobIdSet.contains(nextJob.getId()) && nextJob.getId().equals(info.getJobId())) {
                        GlideinMetric metric = metricsMap.get(info.getQueue());
                        switch (info.getType()) {
                            case PENDING:
                                metric.setPending(metric.getPending() + 1);
                                break;
                            case RUNNING:
                                metric.setRunning(metric.getRunning() + 1);
                                break;
                            case CANCELLED:
                            case COMPLETED:
                            case FAILED:
                            case TIMEOUT:
                                jobCacheIter.remove();
                                break;
                            default:
                                break;
                        }
                        logger.debug("metric: {}", metric.toString());
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return metricsMap;
    }

    @Override
    public void createGlidein(Queue queue) {
        logger.info("ENTERING createGlidein(Queue)");

        if (StringUtils.isNotEmpty(getActiveQueues()) && !getActiveQueues().contains(queue.getName())) {
            logger.warn("queue name is not in active queue list...see etc/org.renci.gate.plugin.topsail.cfg");
            return;
        }

        File submitDir = new File("/tmp", System.getProperty("user.name"));
        submitDir.mkdirs();
        SLURMSSHJob job = null;

        try {
            logger.info("siteInfo: {}", getSite());
            logger.info("queueInfo: {}", queue);
            String hostAllow = "*.unc.edu";
            SLURMSSHSubmitCondorGlideinCallable callable = new SLURMSSHSubmitCondorGlideinCallable(getSite(), queue,
                    submitDir, "glidein", getCollectorHost(), hostAllow, hostAllow, 40);
            job = Executors.newSingleThreadExecutor().submit(callable).get();
            if (job != null && StringUtils.isNotEmpty(job.getId())) {
                logger.info("job.getId(): {}", job.getId());
                jobCache.add(job);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Problem submitting: ", e);
        }
    }

    @Override
    public void deleteGlidein(Queue queue) {
        logger.info("ENTERING deleteGlidein(Queue)");
        if (jobCache.size() > 0) {
            try {
                logger.info("siteInfo: {}", getSite());
                logger.info("queueInfo: {}", queue);
                SLURMSSHJob job = jobCache.get(0);
                SLURMSSHKillCallable callable = new SLURMSSHKillCallable(getSite(), job.getId());
                Executors.newSingleThreadExecutor().submit(callable).get();
                logger.info("job: {}", job.toString());
                jobCache.remove(0);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deletePendingGlideins() {
        logger.info("ENTERING deletePendingGlideins()");
        try {
            SLURMSSHLookupStatusCallable lookupStatusCallable = new SLURMSSHLookupStatusCallable(getSite(), jobCache);
            Set<SLURMJobStatusInfo> jobStatusSet = Executors.newSingleThreadExecutor().submit(lookupStatusCallable)
                    .get();
            for (SLURMJobStatusInfo info : jobStatusSet) {
                if (info.getType().equals(SLURMJobStatusType.PENDING)) {
                    SLURMSSHKillCallable killCallable = new SLURMSSHKillCallable(getSite(), info.getJobId());
                    Executors.newSingleThreadExecutor().submit(killCallable).get();
                }
                // throttle the deleteGlidein calls such that SSH doesn't complain
                Thread.sleep(2000);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

}
