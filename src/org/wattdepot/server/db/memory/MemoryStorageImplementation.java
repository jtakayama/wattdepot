package org.wattdepot.server.db.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import org.wattdepot.util.tstamp.Tstamp;
import org.wattdepot.resource.sensordata.SensorDataStraddle;
import org.wattdepot.resource.sensordata.SensorDataUtils;
import org.wattdepot.resource.sensordata.jaxb.SensorData;
import org.wattdepot.resource.sensordata.jaxb.SensorDataIndex;
import org.wattdepot.resource.source.SourceUtils;
import org.wattdepot.resource.source.jaxb.Source;
import org.wattdepot.resource.source.jaxb.SourceIndex;
import org.wattdepot.resource.source.summary.jaxb.SourceSummary;
import org.wattdepot.resource.user.UserUtils;
import org.wattdepot.resource.user.jaxb.User;
import org.wattdepot.resource.user.jaxb.UserIndex;
import org.wattdepot.server.Server;
import org.wattdepot.server.db.DbBadIntervalException;
import org.wattdepot.server.db.DbImplementation;
import org.wattdepot.util.UriUtils;

/**
 * An in-memory storage implementation for WattDepot. <b>Note:</b> this class persists data
 * <em>in memory only!</em> It does not save any of the data to long-term storage. Therefore it
 * should only be used in special circumstances, such as during system development or performance
 * testing. <b>It is NOT for production use!</b>
 * 
 * @author Robert Brewer
 */
public class MemoryStorageImplementation extends DbImplementation {

  /** Holds the mapping from Source name to Source object. */
  private ConcurrentMap<String, Source> name2SourceHash;
  /** Holds the mapping from Source name to a map of timestamp to SensorData. */
  private ConcurrentMap<String, ConcurrentMap<XMLGregorianCalendar, SensorData>> source2SensorDatasHash;
  /** Holds the mapping from username to a User object. */
  private ConcurrentMap<String, User> name2UserHash;

  /**
   * Constructs a new DbImplementation using ConcurrentHashMaps for storage, with no long-term
   * persistence.
   * 
   * @param server The server this DbImplementation is associated with.
   */
  public MemoryStorageImplementation(Server server) {
    super(server);
  }

  /** {@inheritDoc} */
  @Override
  public void initialize(boolean wipe) {
    // Create the hash maps
    this.name2SourceHash = new ConcurrentHashMap<String, Source>();
    this.source2SensorDatasHash =
        new ConcurrentHashMap<String, ConcurrentMap<XMLGregorianCalendar, SensorData>>();
    this.name2UserHash = new ConcurrentHashMap<String, User>();
    // Since nothing is stored on disk, there is no data to be read into the hash maps
    // wipe parameter is also ignored, since the DB is always wiped on initialization
  }

  /** {@inheritDoc} */
  @Override
  public boolean isFreshlyCreated() {
    // Since this implementation provides no long-term storage, it is always freshly created.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public SourceIndex getSources() {
    SourceIndex index = new SourceIndex();
    // Loop over all Sources in hash
    for (Source source : this.name2SourceHash.values()) {
      // Convert each Source to SourceRef, add to index
      index.getSourceRef().add(SourceUtils.makeSourceRef(source, this.server));
    }
    Collections.sort(index.getSourceRef());
    return index;
  }

  /** {@inheritDoc} */
  @Override
  public Source getSource(String sourceName) {
    if (sourceName == null) {
      return null;
    }
    else {
      return this.name2SourceHash.get(sourceName);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SourceSummary getSourceSummary(String sourceName) {
    if (sourceName == null) {
      // null or non-existent source name
      return null;
    }
    Source baseSource = this.name2SourceHash.get(sourceName);
    if (baseSource == null) {
      return null;
    }
    SourceSummary summary = new SourceSummary();
    summary.setHref(SourceUtils.sourceToUri(sourceName, this.server.getHostName()));
    summary.setTotalSensorDatas(0);
    // Want to go through sensordata for base source, and all subsources recursively
    List<Source> sourceList = getAllNonVirtualSubSources(baseSource);
    XMLGregorianCalendar firstTimestamp = null, lastTimestamp = null, dataTimestamp;
    long dataCount = 0;
    for (Source subSource : sourceList) {
      String subSourceName = subSource.getName();
      // Retrieve this Source's map of timestamps to SensorData
      ConcurrentMap<XMLGregorianCalendar, SensorData> sensorDataMap =
          this.source2SensorDatasHash.get(subSourceName);
      if (sensorDataMap != null) {
        // Loop over all SensorData in hash
        for (SensorData data : sensorDataMap.values()) {
          dataCount++;
          dataTimestamp = data.getTimestamp();
          if (firstTimestamp == null) {
            firstTimestamp = dataTimestamp;
          }
          if (lastTimestamp == null) {
            lastTimestamp = dataTimestamp;
          }
          if (dataTimestamp.compare(firstTimestamp) == DatatypeConstants.LESSER) {
            firstTimestamp = dataTimestamp;
          }
          if (dataTimestamp.compare(lastTimestamp) == DatatypeConstants.GREATER) {
            lastTimestamp = dataTimestamp;
          }
        }
      }
    }
    summary.setFirstSensorData(firstTimestamp);
    summary.setLastSensorData(lastTimestamp);
    summary.setTotalSensorDatas(dataCount);
    return summary;
  }

  /**
   * Given a base Source, return a list of all non-virtual Sources that are subsources of the base
   * Source. This is done recursively, so virtual sources can point to other virtual sources.
   * 
   * @param baseSource The Source to start from.
   * @return A list of all non-virtual Sources that are subsources of the base Source.
   */
  private List<Source> getAllNonVirtualSubSources(Source baseSource) {
    List<Source> sourceList = new ArrayList<Source>();
    if (baseSource.isVirtual()) {
      List<Source> subSources = getAllSubSources(baseSource);
      for (Source subSource : subSources) {
        sourceList.addAll(getAllNonVirtualSubSources(subSource));
      }
      return sourceList;
    }
    else {
      sourceList.add(baseSource);
      return sourceList;
    }
  }

  /**
   * Given a Source, returns a List of Sources corresponding to any subsources of the given Source.
   * 
   * @param source The parent Source.
   * @return A List of Sources that are subsources of the given Source, or null if there are none.
   */
  private List<Source> getAllSubSources(Source source) {
    if (source.isSetSubSources()) {
      List<Source> sourceList = new ArrayList<Source>();
      for (String subSourceUri : source.getSubSources().getHref()) {
        Source subSource = getSource(UriUtils.getUriSuffix(subSourceUri));
        if (subSource != null) {
          sourceList.add(subSource);
        }
      }
      return sourceList;
    }
    else {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean storeSource(Source source) {
    if (source == null) {
      return false;
    }
    else {
      Source previousValue = this.name2SourceHash.putIfAbsent(source.getName(), source);
      // putIfAbsent returns the previous value that ended up in the hash, so if we get a null then
      // no value was previously stored, so we succeeded. If we get anything else, then there was
      // already a value in the hash for this username, so we failed.
      return (previousValue == null);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteSource(String sourceName) {
    if (sourceName == null) {
      return false;
    }
    else {
      // First delete the hash of sensor data for this Source. We ignore the return value since
      // we only need to ensure that there is no sensor data leftover.
      deleteSensorData(sourceName);
      // remove() returns the value for the key, or null if there was no value in the hash. So
      // return true unless we got a null.
      return (this.name2SourceHash.remove(sourceName) != null);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SensorDataIndex getSensorDataIndex(String sourceName) {
    if (sourceName == null) {
      return null;
    }
    else if (this.name2SourceHash.get(sourceName) == null) {
      // Unknown Source name, therefore no possibility of SensorData
      return null;
    }
    else {
      SensorDataIndex index = new SensorDataIndex();
      // Retrieve this Source's map of timestamps to SensorData
      ConcurrentMap<XMLGregorianCalendar, SensorData> sensorDataMap =
          this.source2SensorDatasHash.get(sourceName);
      // If there is any sensor data for this Source
      if (sensorDataMap != null) {
        // Loop over all SensorData in hash
        for (SensorData data : sensorDataMap.values()) {
          // Convert each SensorData to SensorDataRef, add to index
          index.getSensorDataRef().add(SensorDataUtils.makeSensorDataRef(data, this.server));
        }
      }
      Collections.sort(index.getSensorDataRef());
      return index;
    }
  }

  /** {@inheritDoc} */
  @Override
  public SensorDataIndex getSensorDataIndex(String sourceName, XMLGregorianCalendar startTime,
      XMLGregorianCalendar endTime) throws DbBadIntervalException {
    if ((sourceName == null) || (startTime == null) || (endTime == null)) {
      return null;
    }
    else if (this.name2SourceHash.get(sourceName) == null) {
      // Unknown Source name, therefore no possibility of SensorData
      return null;
    }
    else if (startTime.compare(endTime) == DatatypeConstants.GREATER) {
      // startTime > endTime, which is bogus
      throw new DbBadIntervalException(startTime, endTime);
    }
    else {
      SensorDataIndex index = new SensorDataIndex();
      // Retrieve this Source's map of timestamps to SensorData
      ConcurrentMap<XMLGregorianCalendar, SensorData> sensorDataMap =
          this.source2SensorDatasHash.get(sourceName);
      // If there is any sensor data for this Source
      if (sensorDataMap != null) {
        // Loop over all SensorData in hash
        for (SensorData data : sensorDataMap.values()) {
          // Only interested in SensorData that is startTime <= data <= endTime
          int startComparison = data.getTimestamp().compare(startTime);
          int endComparison = data.getTimestamp().compare(endTime);
          if ((startComparison == DatatypeConstants.EQUAL)
              || (endComparison == DatatypeConstants.EQUAL)
              || ((startComparison == DatatypeConstants.GREATER) && (endComparison == DatatypeConstants.LESSER))) {
            // convert each matching SensorData to SensorDataRef, add to index
            index.getSensorDataRef().add(SensorDataUtils.makeSensorDataRef(data, this.server));
          }
        }
      }
      Collections.sort(index.getSensorDataRef());
      return index;
    }
  }

  /** {@inheritDoc} */
  @Override
  public SensorData getSensorData(String sourceName, XMLGregorianCalendar timestamp) {
    if ((sourceName == null) || (timestamp == null)) {
      return null;
    }
    else {
      // Retrieve this Source's map of timestamps to SensorData
      ConcurrentMap<XMLGregorianCalendar, SensorData> sensorDataMap =
          this.source2SensorDatasHash.get(sourceName);
      // If there is any sensor data for this Source
      if (sensorDataMap == null) {
        return null;
      }
      else {
        return sensorDataMap.get(timestamp);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasSensorData(String sourceName, XMLGregorianCalendar timestamp) {
    return (getSensorData(sourceName, timestamp) != null);
  }

  /** {@inheritDoc} */
  @Override
  public boolean storeSensorData(SensorData data) {
    if (data == null) {
      return false;
    }
    else {
      // SensorData resources contain the URI of their Source, so the source name can be found by
      // taking everything after the last "/" in the URI.
      String sourceName = data.getSource().substring(data.getSource().lastIndexOf('/') + 1);
      // Retrieve this Source's map of timestamps to SensorData
      ConcurrentMap<XMLGregorianCalendar, SensorData> sensorDataMap =
          this.source2SensorDatasHash.get(sourceName);
      // If there is no sensor data for this Source yet
      if (sensorDataMap == null) {
        // Create the sensorDataMap
        sensorDataMap = new ConcurrentHashMap<XMLGregorianCalendar, SensorData>();
        // add to SenorDataHash in thread-safe manner (in case someone beats us to it)
        this.source2SensorDatasHash.putIfAbsent(sourceName, sensorDataMap);
        // Don't need to check result, since we only care that there is a hash we can store to,
        // not whether the one we created actually got stored.
      }
      // Try putting the new SensorData into the hash for the appropriate source
      SensorData previousValue = sensorDataMap.putIfAbsent(data.getTimestamp(), data);
      // putIfAbsent returns the previous value that ended up in the hash, so if we get a null then
      // no value was previously stored, so we succeeded. If we get anything else, then there was
      // already a value in the hash for this username, so we failed.
      return (previousValue == null);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteSensorData(String sourceName, XMLGregorianCalendar timestamp) {
    if ((sourceName == null) || (timestamp == null)) {
      return false;
    }
    else {
      // Retrieve this Source's map of timestamps to SensorData
      ConcurrentMap<XMLGregorianCalendar, SensorData> sensorDataMap =
          this.source2SensorDatasHash.get(sourceName);
      // If there is any sensor data for this Source
      if (sensorDataMap == null) {
        return false;
      }
      else {
        // remove() returns the value for the key, or null if there was no value in the hash. So
        // return true unless we got a null.
        return (sensorDataMap.remove(timestamp) != null);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteSensorData(String sourceName) {
    if (sourceName == null) {
      return false;
    }
    else {
      // Delete the hash of sensor data for this Source. If the source doesn't exist or there is no
      // sensor data, we'll get a null.
      return (this.source2SensorDatasHash.remove(sourceName) != null);
    }
  }

  /**
   * Returns a SensorDataStraddle that straddles the given timestamp, using SensorData from the
   * given source. Note that a virtual source contains no SensorData directly, so this method will
   * always return null if the given sourceName is a virtual source. To obtain a list of
   * SensorDataStraddles for all the non-virtual subsources of a virtual source, see
   * getSensorDataStraddleList.
   * 
   * If the given timestamp corresponds to an actual SensorData, then return a degenerate
   * SensorDataStraddle with both ends of the straddle set to the actual SensorData.
   * 
   * @param sourceName The name of the source to generate the straddle from.
   * @param timestamp The timestamp of interest in the straddle.
   * @return A SensorDataStraddle that straddles the given timestamp. Returns null if: parameters
   * are null, the source doesn't exist, source has no sensor data, or there is no sensor data that
   * straddles the timestamp.
   * @see org.wattdepot.server.db.memory#getSensorDataStraddleList
   */
  @Override
  public SensorDataStraddle getSensorDataStraddle(String sourceName, XMLGregorianCalendar timestamp) {
    // This is a kludge, create sentinels for times way outside our expected range
    SensorData beforeSentinel, afterSentinel;
    try {
      beforeSentinel =
          SensorDataUtils.makeSensorData(Tstamp.makeTimestamp("1700-01-01T00:00:00.000-10:00"), "",
              "", null);
      afterSentinel =
          SensorDataUtils.makeSensorData(Tstamp.makeTimestamp("3000-01-01T00:00:00.000-10:00"), "",
              "", null);
    }
    catch (Exception e) {
      throw new RuntimeException(
          "Creating timestamp from static string failed. This should never happen", e);
    }
    // initialize beforeData & afterData to sentinel values
    SensorData beforeData = beforeSentinel, afterData = afterSentinel;
    if ((sourceName == null) || (timestamp == null)) {
      return null;
    }
    Source source = this.name2SourceHash.get(sourceName);
    if (source == null) {
      return null;
    }
    XMLGregorianCalendar dataTimestamp;
    int dataTimestampCompare;
    // Retrieve this Source's map of timestamps to SensorData
    ConcurrentMap<XMLGregorianCalendar, SensorData> sensorDataMap =
        this.source2SensorDatasHash.get(sourceName);
    if (sensorDataMap == null) {
      return null;
    }
    else {
      // Loop over all SensorData in hash
      for (SensorData data : sensorDataMap.values()) {
        dataTimestamp = data.getTimestamp();
        dataTimestampCompare = dataTimestamp.compare(timestamp);
        if (dataTimestampCompare == DatatypeConstants.EQUAL) {
          // There is SensorData for the requested timestamp, so return degenerate
          // SensorDataStraddle
          return new SensorDataStraddle(timestamp, data, data);
        }
        if ((dataTimestamp.compare(beforeData.getTimestamp()) == DatatypeConstants.GREATER)
            && (dataTimestampCompare == DatatypeConstants.LESSER)) {
          // found closer beforeData
          beforeData = data;
        }
        else if ((dataTimestamp.compare(afterData.getTimestamp()) == DatatypeConstants.LESSER)
            && (dataTimestampCompare == DatatypeConstants.GREATER)) {
          // found closer afterData
          afterData = data;
        }
      }
      if (beforeData.equals(beforeSentinel) || afterData.equals(afterSentinel)) {
        // one of the sentinels never got changed, so no straddle
        return null;
      }
      else {
        return new SensorDataStraddle(timestamp, beforeData, afterData);
      }
    }
  }

  /**
   * Returns a list of SensorDataStraddles that straddle the given timestamp, using SensorData from
   * all non-virtual subsources of the given source. If the given source is non-virtual, then the
   * result will be a list containing at a single SensorDataStraddle, or null. In the case of a
   * non-virtual source, you might as well use getSensorDataStraddle.
   * 
   * @param sourceName The name of the source to generate the straddle from.
   * @param timestamp The timestamp of interest in the straddle.
   * @return A list of SensorDataStraddles that straddle the given timestamp. Returns null if:
   * parameters are null, the source doesn't exist, or there is no sensor data that straddles the
   * timestamp.
   * @see org.wattdepot.server.db.memory#getSensorDataStraddle
   */
  @Override
  public List<SensorDataStraddle> getSensorDataStraddleList(String sourceName,
      XMLGregorianCalendar timestamp) {
    if ((sourceName == null) || (timestamp == null)) {
      return null;
    }
    Source baseSource = this.name2SourceHash.get(sourceName);
    if (baseSource == null) {
      return null;
    }
    // Want to go through sensordata for base source, and all subsources recursively
    List<Source> sourceList = getAllNonVirtualSubSources(baseSource);
    List<SensorDataStraddle> straddleList = new ArrayList<SensorDataStraddle>();
    for (Source subSource : sourceList) {
      String subSourceName = subSource.getName();
      SensorDataStraddle straddle = getSensorDataStraddle(subSourceName, timestamp);
      if (straddle != null) {
        straddleList.add(straddle);
      }
    }
    if (straddleList.isEmpty()) {
      return null;
    }
    else {
      return straddleList;
    }
  }

  /** {@inheritDoc} */
  @Override
  public UserIndex getUsers() {
    UserIndex index = new UserIndex();
    // Loop over all Users in hash
    for (User user : this.name2UserHash.values()) {
      // Convert each Source to SourceRef, add to index
      index.getUserRef().add(UserUtils.makeUserRef(user, this.server));
    }
    return index;
  }

  /** {@inheritDoc} */
  @Override
  public User getUser(String username) {
    if (username == null) {
      return null;
    }
    else {
      return this.name2UserHash.get(username);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean storeUser(User user) {
    if (user == null) {
      return false;
    }
    else {
      User previousValue = this.name2UserHash.putIfAbsent(user.getEmail(), user);
      // putIfAbsent returns the previous value that ended up in the hash, so if we get a null then
      // no value was previously stored, so we succeeded. If we get anything else, then there was
      // already a value in the hash for this username, so we failed.
      return (previousValue == null);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteUser(String username) {
    if (username == null) {
      return false;
    }
    else {
      // Loop over all Sources in hash, looking for ones owned by username
      for (Source source : this.name2SourceHash.values()) {
        // Source resources contain the URI of their owner, so the owner username can be found by
        // taking everything after the last "/" in the URI.
        String ownerName = source.getOwner().substring(source.getOwner().lastIndexOf('/') + 1);
        // If this User owns the Source, delete the Source
        if (ownerName.equals(username)) {
          deleteSource(source.getName());
        }
      }
      // remove() returns the value for the key, or null if there was no value in the hash. So
      // return true unless we got a null.
      return (this.name2UserHash.remove(username) != null);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean performMaintenance() {
    // ConcurrentHashMaps don't need maintenance, so just return true.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean indexTables() {
    // ConcurrentHashMaps don't need indexes, so just return true.
    return true;
  }
}
