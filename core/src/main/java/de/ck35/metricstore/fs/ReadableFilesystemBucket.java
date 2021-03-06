package de.ck35.metricstore.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.base.Predicate;

import de.ck35.metricstore.MetricBucket;
import de.ck35.metricstore.StoredMetric;
import de.ck35.metricstore.util.io.MetricsIOException;
import de.ck35.metricstore.util.io.ObjectNodeReader;

/**
 * A read-only implementation of a {@link MetricBucket}. 
 *
 * @author Christian Kaspari
 * @since 1.0.0
 * @see WritableFilesystemBucket
 */
public class ReadableFilesystemBucket implements MetricBucket {

	private final BucketData bucketData;
	private final Function<ObjectNode, DateTime> timestampFunction;
	private final Function<Path, ObjectNodeReader> readerFactory;
	
	public ReadableFilesystemBucket(BucketData bucketData,
      	    						Function<ObjectNode, DateTime> timestampFunction,
      	    						Function<Path, ObjectNodeReader> readerFactory) {
		this.bucketData = bucketData;
		this.timestampFunction = timestampFunction;
		this.readerFactory = readerFactory;
	}
	
	@Override
	public String getName() {
		return bucketData.getName();
	}
	@Override
	public String getType() {
		return bucketData.getType();
	}
	public BucketData getBucketData() {
		return bucketData;
	}
	
	public void read(Interval interval, Predicate<StoredMetric> predicate) throws InterruptedException {
		try {
			DateTime start = interval.getStart().withZone(DateTimeZone.UTC).withSecondOfMinute(0).withMillisOfSecond(0);
			DateTime end = interval.getEnd().withZone(DateTimeZone.UTC).withSecondOfMinute(0).withMillisOfSecond(0);
			for(DateTime current = start ; current.isBefore(end) ; current = current.plusMinutes(1)) {
				PathFinder pathFinder = pathFinder(current);
				Path dayFile = pathFinder.getDayFilePath();
				if(Files.isRegularFile(dayFile)) {
					try(StoredObjectNodeReader reader = createReader(dayFile)) {
						read(current, end, reader, predicate);
						current = atEndOfDay(current);
					}
				} else {
					Path minuteFile = pathFinder.getMinuteFilePath();
					if(Files.isRegularFile(minuteFile)) {
						try(StoredObjectNodeReader reader = createReader(minuteFile)) {
							current = read(current, end, reader, predicate);
						}
					} else {
					    if(!Files.isDirectory(minuteFile.getParent())) { //Day folder does not exist
					        current = atEndOfDay(current);
					        if(!Files.isDirectory(minuteFile.getParent().getParent())) { //Month folder does not exist
					            current = atEndOfMonth(current);
					            if(!Files.isDirectory(minuteFile.getParent().getParent().getParent())) { //Year folder does not exist
					                current = atEndOfYear(current);
					            }
					        }
					    }
					}
				}
			}
		} catch(IOException e) {
			throw new MetricsIOException("Could not close a resource while reading from bucket: '" + bucketData + "'!", e);
		}
	}

	protected DateTime read(DateTime start, DateTime end, StoredObjectNodeReader reader, Predicate<StoredMetric> predicate) throws InterruptedException {
		DateTime current = start;
		for(StoredMetric next = reader.read() ; next != null ; next = reader.read()) {
			current = next.getTimestamp();
			if(current.isBefore(start)) {
				continue;
			}
			if(current.isEqual(end) || current.isAfter(end)) {
				return current;
			}
			current = next.getTimestamp();
			if(!predicate.apply(next)) {
				throw new InterruptedException();
			}
		}
		return current;
	}
	
	protected StoredObjectNodeReader createReader(Path path) {
		return new StoredObjectNodeReader(this, readerFactory.apply(path), timestampFunction);
	}
	
	public PathFinder pathFinder(DateTime timestamp) {
		return new PathFinder(timestamp, bucketData.getBasePath());
	}
	public PathFinder pathFinder(LocalDate date) {
		return new PathFinder(date, bucketData.getBasePath());
	}
	
	public static DateTime atEndOfYear(DateTime current) {
        return current.withMonthOfYear(1).withDayOfMonth(1).plusYears(1).minusDays(1);
    }
    public static DateTime atEndOfMonth(DateTime current) {
        return current.withDayOfMonth(1).plusMonths(1).minusDays(1);
    }
    public static DateTime atEndOfDay(DateTime current) {
        return current.withHourOfDay(23).withMinuteOfHour(59);
    }
}