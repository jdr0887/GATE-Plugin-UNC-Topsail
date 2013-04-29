package org.renci.gate.plugin.topsail;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.renci.gate.AbstractGATEService;
import org.renci.gate.GlideinMetric;
import org.renci.jlrm.JLRMException;
import org.renci.jlrm.Queue;
import org.renci.jlrm.slurm.SLURMJobStatusInfo;
import org.renci.jlrm.slurm.SLURMJobStatusType;
import org.renci.jlrm.slurm.ssh.SLURMSSHFactory;
import org.renci.jlrm.slurm.ssh.SLURMSSHJob;
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
        return null;
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
            SLURMSSHFactory factory = SLURMSSHFactory.getInstance(getSite());
            String hostAllow = "*.unc.edu";
            job = factory.submitGlidein(submitDir, getCollectorHost(), queue, 40, "glidein", hostAllow, hostAllow);
            if (job != null && StringUtils.isNotEmpty(job.getId())) {
                logger.info("job.getId(): {}", job.getId());
                jobCache.add(job);
            }
        } catch (JLRMException e) {
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
                SLURMSSHFactory factory = SLURMSSHFactory.getInstance(getSite());
                SLURMSSHJob job = jobCache.get(0);
                logger.info("job: {}", job.toString());
                factory.killGlidein(job.getId());
                jobCache.remove(0);
            } catch (JLRMException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deletePendingGlideins() {
        logger.info("ENTERING deletePendingGlideins()");
        try {
            SLURMSSHFactory factory = SLURMSSHFactory.getInstance(getSite());
            Set<SLURMJobStatusInfo> jobStatusSet = factory.lookupStatus(jobCache);
            for (SLURMJobStatusInfo info : jobStatusSet) {
                if (info.getType().equals(SLURMJobStatusType.PENDING)) {
                    factory.killGlidein(info.getJobId());
                }
                try {
                    // throttle the deleteGlidein calls such that SSH doesn't complain
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (JLRMException e) {
            e.printStackTrace();
        }
    }

}
