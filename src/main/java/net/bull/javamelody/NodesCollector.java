/*
 * Copyright 2008-2011 by Emeric Vernat
 */
package net.bull.javamelody;

import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Collector of data for Hudson's nodes (slaves in general)
 * 
 * @author Emeric Vernat
 */
public class NodesCollector {
	private static class RemoteCollector extends Collector {
		RemoteCollector(String application, List<Counter> counters) {
			super(application, counters);
		}

		/** {@inheritDoc} */
		@Override
		void collectLocalContextWithoutErrors() {
			// no local collect
		}
	}

	private final boolean monitoringDisabled;
	private final Timer timer;
	private final Collector collector;
	private List<JavaInformations> lastJavaInformationsList;

	/**
	 * Constructor.
	 * 
	 * @param filter Http filter to get the scheduling timer
	 */
	public NodesCollector(MonitoringFilter filter) {
		super();
		this.monitoringDisabled = Boolean.parseBoolean(Parameters.getParameter(Parameter.DISABLED));
		if (!monitoringDisabled) {
			this.timer = filter.getFilterContext().getTimer();
			final List<Counter> counters = Collections.singletonList(CounterRunListener
					.getBuildCounter());
			this.collector = new RemoteCollector("nodes", counters);
		} else {
			this.timer = null;
			this.collector = null;
		}
	}

	/**
	 * Initialization.
	 */
	public void init() {
		if (monitoringDisabled) {
			return;
		}
		final int periodMillis = Parameters.getResolutionSeconds() * 1000;
		// schedule of a background task, with an asynchronous execution now to
		// initialize the data
		final TimerTask collectTask = new TimerTask() {
			/** {@inheritDoc} */
			@Override
			public void run() {
				// errors must not happen in this task
				collectWithoutErrors();
			}
		};
		timer.schedule(collectTask, 5000, periodMillis);

		// schedule to send reports by email
		if (Parameters.getParameter(Parameter.MAIL_SESSION) != null
				&& Parameters.getParameter(Parameter.ADMIN_EMAILS) != null) {
			scheduleReportMailForSlaves();
		}
	}

	/**
	 * Schedule a collect now (used to collect data on new online nodes)
	 */
	public void scheduleCollectNow() {
		final TimerTask collectTask = new TimerTask() {
			/** {@inheritDoc} */
			@Override
			public void run() {
				// errors must not happen in this task
				collectWithoutErrors();
			}
		};
		timer.schedule(collectTask, 0);
	}

	/**
	 * Stop the collector.
	 */
	public void stop() {
		if (monitoringDisabled) {
			return;
		}
		timer.cancel();
		collector.stop();
	}

	public void collectWithoutErrors() {
		try {
			lastJavaInformationsList = RemoteCallHelper.collectJavaInformationsList();
			collector.collectWithoutErrors(lastJavaInformationsList);
		} catch (final Throwable t) { // NOPMD
			LOG.warn("exception while collecting data", t);
		}
	}

	void scheduleReportMailForSlaves() {
		for (final Period period : MailReport.getMailPeriods()) {
			scheduleReportMailForSlaves(period);
		}
	}

	void scheduleReportMailForSlaves(final Period period) {
		assert period != null;
		final TimerTask task = new TimerTask() {
			/** {@inheritDoc} */
			@Override
			public void run() {
				try {
					// send the report
					new MailReport().sendReportMail(getCollector(), true,
							getLastJavaInformationsList(), period);
				} catch (final Throwable t) { // NOPMD
					// no error in this task
					LOG.warn("sending mail report failed", t);
				}
				// schedule again at the same hour next day or next week without
				// using a fixed period, because some days have 23h or 25h and
				// we do not want to have a change in the hour for sending the
				// report
				scheduleReportMailForSlaves(period);
			}
		};

		// schedule the task once
		timer.schedule(task, MailReport.getNextExecutionDate(period));
	}

	Collector getCollector() {
		return collector;
	}

	List<JavaInformations> getLastJavaInformationsList() {
		return lastJavaInformationsList;
	}

	public boolean isMonitoringDisabled() {
		return monitoringDisabled;
	}
}