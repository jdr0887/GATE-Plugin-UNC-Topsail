package org.renci.gate.plugin.topsail;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import org.apache.commons.lang.StringUtils;
import org.renci.gate.AbstractGATEService;
import org.renci.gate.GATEException;
import org.renci.gate.GlideinMetric;
import org.renci.jlrm.JLRMException;
import org.renci.jlrm.Queue;
import org.renci.jlrm.commons.ssh.SSHConnectionUtil;
import org.renci.jlrm.slurm.SLURMJobStatusInfo;
import org.renci.jlrm.slurm.SLURMJobStatusType;
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

    public TopsailGATEService() {
        super();
    }

    @Override
    public Boolean isValid() throws GATEException {
        logger.info("ENTERING isValid()");
        try {
            String results = SSHConnectionUtil.execute("ls /scratch/projects/mapseq | wc -l", getSite().getUsername(),
                    getSite().getSubmitHost());
            if (StringUtils.isNotEmpty(results) && Integer.valueOf(results.trim()) > 0) {
                return true;
            }
        } catch (NumberFormatException | JLRMException e) {
            throw new GATEException(e);
        }
        return false;
    }

    @Override
    public Map<String, GlideinMetric> lookupMetrics() throws GATEException {
        logger.info("ENTERING lookupMetrics()");
        Map<String, GlideinMetric> metricsMap = new HashMap<String, GlideinMetric>();

        try {
            SLURMSSHLookupStatusCallable callable = new SLURMSSHLookupStatusCallable(getSite());
            Set<SLURMJobStatusInfo> jobStatusSet = Executors.newSingleThreadExecutor().submit(callable).get();
            logger.debug("jobStatusSet.size(): {}", jobStatusSet.size());

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

        } catch (Exception e) {
            throw new GATEException(e);
        }

        return metricsMap;
    }

    @Override
    public void createGlidein(Queue queue) throws GATEException {
        logger.info("ENTERING createGlidein(Queue)");

        if (StringUtils.isNotEmpty(getActiveQueues()) && !getActiveQueues().contains(queue.getName())) {
            logger.warn("queue name is not in active queue list...see etc/org.renci.gate.plugin.topsail.cfg");
            return;
        }

        File submitDir = new File("/tmp", System.getProperty("user.name"));
        submitDir.mkdirs();

        try {
            logger.info("siteInfo: {}", getSite());
            logger.info("queueInfo: {}", queue);
            String hostAllow = "*.unc.edu";
            SLURMSSHSubmitCondorGlideinCallable callable = new SLURMSSHSubmitCondorGlideinCallable();
            callable.setCollectorHost(getCollectorHost());
            callable.setUsername(System.getProperty("user.name"));
            callable.setSite(getSite());
            callable.setJobName("glidein");
            callable.setQueue(queue);
            callable.setSubmitDir(submitDir);
            callable.setRequiredMemory(40);
            callable.setHostAllowRead(hostAllow);
            callable.setHostAllowWrite(hostAllow);
            Executors.newSingleThreadExecutor().submit(callable).get();
        } catch (Exception e) {
            throw new GATEException(e);
        }
    }

    @Override
    public void deleteGlidein(Queue queue) throws GATEException {
        logger.info("ENTERING deleteGlidein(Queue)");
        try {
            logger.info("siteInfo: {}", getSite());
            logger.info("queueInfo: {}", queue);
            SLURMSSHLookupStatusCallable lookupStatusCallable = new SLURMSSHLookupStatusCallable(getSite());
            Set<SLURMJobStatusInfo> jobStatusSet = Executors.newSingleThreadExecutor().submit(lookupStatusCallable)
                    .get();
            SLURMSSHKillCallable callable = new SLURMSSHKillCallable(getSite(), jobStatusSet.iterator().next()
                    .getJobId());
            Executors.newSingleThreadExecutor().submit(callable).get();
        } catch (Exception e) {
            throw new GATEException(e);
        }
    }

    @Override
    public void deletePendingGlideins() throws GATEException {
        logger.info("ENTERING deletePendingGlideins()");
        try {
            SLURMSSHLookupStatusCallable lookupStatusCallable = new SLURMSSHLookupStatusCallable(getSite());
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
        } catch (Exception e) {
            throw new GATEException(e);
        }
    }

}
