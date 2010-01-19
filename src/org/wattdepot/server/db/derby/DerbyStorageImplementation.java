package org.wattdepot.server.db.derby;

import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import org.wattdepot.resource.property.jaxb.Properties;
import org.wattdepot.resource.sensordata.SensorDataStraddle;
import org.wattdepot.resource.sensordata.StraddleList;
import org.wattdepot.resource.sensordata.jaxb.SensorData;
import org.wattdepot.resource.sensordata.jaxb.SensorDataIndex;
import org.wattdepot.resource.sensordata.jaxb.SensorDataRef;
import org.wattdepot.resource.source.jaxb.Source;
import org.wattdepot.resource.source.jaxb.SourceIndex;
import org.wattdepot.resource.source.jaxb.SourceRef;
import org.wattdepot.resource.source.jaxb.Sources;
import org.wattdepot.resource.source.jaxb.SubSources;
import org.wattdepot.resource.source.summary.jaxb.SourceSummary;
import org.wattdepot.resource.user.jaxb.User;
import org.wattdepot.resource.user.jaxb.UserIndex;
import org.wattdepot.resource.user.jaxb.UserRef;
import org.wattdepot.server.Server;
import org.wattdepot.server.ServerProperties;
import org.wattdepot.server.db.DbBadIntervalException;
import org.wattdepot.server.db.DbImplementation;
import org.wattdepot.util.StackTrace;
import org.wattdepot.util.tstamp.Tstamp;

/**
 * Provides a implementation of DbImplementation using Derby in embedded mode. Currently it is a
 * hybrid of the MemoryStorageImplementation, with pieces being replaced with Derby code
 * incrementally.
 * 
 * Note: If you are using this implementation as a guide for implementing an alternative database,
 * you should be aware that this implementation does not do connection pooling. It turns out that
 * embedded Derby does not require connection pooling, so it is not present in this code. You will
 * probably want it for your version, of course. Based on code from Hackystat sensorbase.
 * 
 * @author Robert Brewer
 * @author Philip Johnson
 */
public class DerbyStorageImplementation extends DbImplementation {

  private static final String UNABLE_TO_PARSE_PROPERTY_XML =
      "Unable to parse property XML from database ";
  /** Property JAXBContext. */
  private static final JAXBContext propertiesJAXB;
  /** SubSources JAXBContext. */
  private static final JAXBContext subSourcesJAXB;

  // JAXBContexts are thread safe, so we can share them across all instances and threads.
  // https://jaxb.dev.java.net/guide/Performance_and_thread_safety.html
  static {
    try {
      propertiesJAXB =
          JAXBContext.newInstance(org.wattdepot.resource.property.jaxb.Properties.class);
      subSourcesJAXB = JAXBContext.newInstance(org.wattdepot.resource.source.jaxb.SubSources.class);
    }
    catch (Exception e) {
      throw new RuntimeException("Couldn't create JAXB context instance.", e);
    }
  }
  /** The key for putting/retrieving the directory where Derby will create its databases. */
  private static final String derbySystemKey = "derby.system.home";
  /** The JDBC driver. */
  private static final String driver = "org.apache.derby.jdbc.EmbeddedDriver";
  /** The Database name. */
  private static final String dbName = "wattdepot";
  /** The Derby connection URL. */
  private static final String connectionURL = "jdbc:derby:" + dbName + ";create=true";
  /** Indicates whether this database was initialized or was pre-existing. */
  private boolean isFreshlyCreated = false;
  /** The logger message when executing a query. */
  private static final String executeQueryMsg = "Derby: Executing query ";
  /** The logger message for connection closing errors. */
  private static final String errorClosingMsg = "Derby: Error while closing. \n";
  private static final String derbyError = "Derby: Error ";
  /** The SQL state indicating that INSERT tried to add data to a table with a preexisting key. */
  private static final String DUPLICATE_KEY = "23505";

  /**
   * Instantiates the Derby implementation. Throws a Runtime exception if the Derby jar file cannot
   * be found on the classpath.
   * 
   * @param server The server this DbImplementation is associated with.
   */
  public DerbyStorageImplementation(Server server) {
    super(server);
    // Set the directory where the DB will be created and/or accessed.
    // This must happen before loading the driver.
    String dbDir = server.getServerProperties().get(ServerProperties.DB_DIR_KEY);
    System.getProperties().put(derbySystemKey, dbDir);
    // Try to load the derby driver.
    try {
      Class.forName(driver);
    }
    catch (java.lang.ClassNotFoundException e) {
      String msg = "Derby: Exception during DbManager initialization: Derby not on CLASSPATH.";
      this.logger.warning(msg + "\n" + StackTrace.toString(e));
      throw new RuntimeException(msg, e);
    }

  }

  /** {@inheritDoc} */
  @Override
  public void initialize(boolean wipe) {
    try {
      // Create a shutdown hook that shuts down derby.
      Runtime.getRuntime().addShutdownHook(new Thread() {
        /** Run the shutdown hook for shutting down Derby. */
        @Override
        public void run() {
          Connection conn = null;
          try {
            conn = DriverManager.getConnection("jdbc:derby:;shutdown=true");
          }
          catch (Exception e) {
            System.out.println("Derby shutdown hook results: " + e.getMessage());
          }
          finally {
            try {
              conn.close();
            }
            catch (Exception e) { // NOPMD
              // we tried.
            }
          }
        }
      });
      // Initialize the database table structure if necessary.
      this.isFreshlyCreated = !isPreExisting();
      String dbStatusMsg =
          (this.isFreshlyCreated) ? "Derby: uninitialized." : "Derby: previously initialized.";
      this.logger.info(dbStatusMsg);
      if (this.isFreshlyCreated) {
        this.logger.info("Derby: creating DB in: " + System.getProperty(derbySystemKey));
        createTables();
      }
      // Only need to wipe tables if database has already been created and wiping was requested
      else if (wipe) {
        wipeTables();
      }
      // if (server.getServerProperties().compressOnStartup()) {
      // this.logger.info("Derby: compressing database...");
      // compressTables();
      // }
      // if (server.getServerProperties().reindexOnStartup()) {
      // this.logger.info("Derby: reindexing database...");
      // this.logger.info("Derby: reindexing database " + ((indexTables()) ? "OK" : "not OK"));
      // }
    }
    catch (Exception e) {
      String msg = "Derby: Exception during DerbyImplementation initialization:";
      this.logger.warning(msg + "\n" + StackTrace.toString(e));
      throw new RuntimeException(msg, e);
    }
  }

  /**
   * Determine if the database has already been initialized with correct table definitions. Table
   * schemas are checked by seeing if a dummy insert on the table will work OK.
   * 
   * @return True if the database exists and tables are set up correctly.
   * @throws SQLException If problems occur accessing the database or the tables aren't set up
   * correctly.
   */
  private boolean isPreExisting() throws SQLException {
    Connection conn = null;
    Statement s = null;
    try {
      conn = DriverManager.getConnection(connectionURL);
      s = conn.createStatement();
      s.execute(testUserTableStatement);
      s.execute(testSourceTableStatement);
      s.execute(testSensorDataTableStatement);
    }
    catch (SQLException e) {
      String theError = (e).getSQLState();
      if ("42X05".equals(theError)) {
        // Database doesn't exist.
        return false;
      }
      else if ("42X14".equals(theError) || "42821".equals(theError)) {
        // Incorrect table definition.
        throw e;
      }
      else {
        // Unknown SQLException
        throw e;
      }
    }
    finally {
      if (s != null) {
        s.close();
      }
      if (conn != null) {
        conn.close();
      }
    }
    // If table exists will get - WARNING 02000: No row was found
    return true;
  }

  /**
   * Initialize the database by creating tables for each resource type.
   * 
   * @throws SQLException If table creation fails.
   */
  private void createTables() throws SQLException {
    Connection conn = null;
    Statement s = null;
    try {
      conn = DriverManager.getConnection(connectionURL);
      s = conn.createStatement();
      s.execute(createSensorDataTableStatement);
      // s.execute(indexSensorDataTstampStatement);
      s.execute(createUserTableStatement);
      s.execute(createSourceTableStatement);
      s.close();
    }
    finally {
      s.close();
      conn.close();
    }
  }

  /**
   * Wipe the database by deleting all records from each table.
   * 
   * @throws SQLException If table deletion fails.
   */
  private void wipeTables() throws SQLException {
    Connection conn = null;
    Statement s = null;
    try {
      conn = DriverManager.getConnection(connectionURL);
      s = conn.createStatement();
      s.execute("DELETE from WattDepotUser");
      s.execute("DELETE from Source");
      s.execute("DELETE from SensorData");
      s.close();
    }
    finally {
      s.close();
      conn.close();
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isFreshlyCreated() {
    // This value is initialized by initialize()
    return this.isFreshlyCreated;
  }

  /** The SQL string for creating the Source table. */
  private static final String createSourceTableStatement =
      "create table Source  " + "(" + " Name VARCHAR(128) NOT NULL, "
          + " Owner VARCHAR(256) NOT NULL, " + " PublicP SMALLINT NOT NULL, "
          + " Virtual SMALLINT NOT NULL, " + " Coordinates VARCHAR(80), "
          + " Location VARCHAR(256), " + " Description VARCHAR(1024), "
          + " SubSources VARCHAR(32000), " + " Properties VARCHAR(32000), "
          + " LastMod TIMESTAMP NOT NULL, " + " PRIMARY KEY (Name) " + ")";

  /** An SQL string to test whether the Source table exists and has the correct schema. */
  private static final String testSourceTableStatement =
      " UPDATE Source SET "
          + " Name = 'db-test-source', "
          + " Owner = 'http://server.wattdepot.org/wattdepot/users/db-test-user', "
          + " PublicP = 0, "
          + " Virtual = 1, "
          + " Coordinates = '21.30078,-157.819129,41', "
          + " Location = 'Some place', "
          + " Description = 'A test source.', "
          + " SubSources = '<SubSources><Href>http://server.wattdepot.org:1234/wattdepot/sources/SIM_HONOLULU_8</Href><Href>http://server.wattdepot.org:1234/wattdepot/sources/SIM_HONOLULU_9</Href></SubSources>', "
          + " Properties = '<Properties><Property><Key>carbonIntensity</Key><Value>2120</Value></Property></Properties>', "
          + " LastMod = '" + new Timestamp(new Date().getTime()).toString() + "' " + " WHERE 1=3";

  /** {@inheritDoc} */
  @Override
  public SourceIndex getSourceIndex() {
    SourceIndex index = new SourceIndex();
    String statement =
        "SELECT Name, Owner, PublicP, Virtual, Coordinates, Location, Description FROM Source ORDER BY Name";
    Connection conn = null;
    PreparedStatement s = null;
    ResultSet rs = null;
    SourceRef ref;
    try {
      conn = DriverManager.getConnection(connectionURL);
      server.getLogger().fine(executeQueryMsg + statement);
      s = conn.prepareStatement(statement);
      rs = s.executeQuery();
      while (rs.next()) {
        ref = new SourceRef();
        String name = rs.getString("Name");
        ref.setName(name);
        ref.setOwner(rs.getString("Owner"));
        ref.setPublic(rs.getBoolean("PublicP"));
        ref.setVirtual(rs.getBoolean("Virtual"));
        ref.setCoordinates(rs.getString("Coordinates"));
        ref.setLocation(rs.getString("Location"));
        ref.setDescription(rs.getString("Description"));
        ref.setHref(name, this.server);
        index.getSourceRef().add(ref);
      }
    }
    catch (SQLException e) {
      this.logger.info("DB: Error in getSources()" + StackTrace.toString(e));
    }
    finally {
      try {
        rs.close();
        s.close();
        conn.close();
      }
      catch (SQLException e) {
        this.logger.warning(errorClosingMsg + StackTrace.toString(e));
      }
    }
    // Collections.sort(index.getSourceRef());
    return index;
  }

  /**
   * Converts a database row from source table to a Source object. The caller should have advanced
   * the cursor to the next row via rs.next() before calling this method.
   * 
   * @param rs The result set to be examined.
   * @return The new Source object.
   */
  private Source resultSetToSource(ResultSet rs) {
    Source source = new Source();
    String xmlString;

    try {
      source.setName(rs.getString("Name"));
      source.setOwner(rs.getString("Owner"));
      source.setPublic(rs.getBoolean("PublicP"));
      source.setVirtual(rs.getBoolean("Virtual"));
      source.setCoordinates(rs.getString("Coordinates"));
      source.setLocation(rs.getString("Location"));
      source.setDescription(rs.getString("Description"));
      xmlString = rs.getString("SubSources");
      if (xmlString != null) {
        try {
          Unmarshaller unmarshaller = subSourcesJAXB.createUnmarshaller();
          source.setSubSources((SubSources) unmarshaller.unmarshal(new StringReader(xmlString)));
        }
        catch (JAXBException e) {
          // Got some XML from DB we can't parse
          this.logger.warning(UNABLE_TO_PARSE_PROPERTY_XML + StackTrace.toString(e));
        }
      }
      xmlString = rs.getString("Properties");
      if (xmlString != null) {
        try {
          Unmarshaller unmarshaller = propertiesJAXB.createUnmarshaller();
          source.setProperties((Properties) unmarshaller.unmarshal(new StringReader(xmlString)));
        }
        catch (JAXBException e) {
          // Got some XML from DB we can't parse
          this.logger.warning(UNABLE_TO_PARSE_PROPERTY_XML + StackTrace.toString(e));
        }
      }
    }
    catch (SQLException e) {
      this.logger.info("DB: Error in getSource()" + StackTrace.toString(e));
      return null;
    }
    return source;
  }

  /** {@inheritDoc} */
  @Override
  public Sources getSources() {
    Sources sources = new Sources();
    String statement = "SELECT * FROM Source ORDER BY Name";
    Connection conn = null;
    PreparedStatement s = null;
    ResultSet rs = null;
    try {
      conn = DriverManager.getConnection(connectionURL);
      server.getLogger().fine(executeQueryMsg + statement);
      s = conn.prepareStatement(statement);
      rs = s.executeQuery();
      while (rs.next()) {
        sources.getSource().add(resultSetToSource(rs));
      }
    }
    catch (SQLException e) {
      this.logger.info("DB: Error in getSources()" + StackTrace.toString(e));
    }
    finally {
      try {
        rs.close();
        s.close();
        conn.close();
      }
      catch (SQLException e) {
        this.logger.warning(errorClosingMsg + StackTrace.toString(e));
      }
    }
    return sources;
  }

  /** {@inheritDoc} */
  @Override
  public Source getSource(String sourceName) {
    if (sourceName == null) {
      return null;
    }
    else {
      String statement = "SELECT * FROM Source WHERE Name = ?";
      Connection conn = null;
      PreparedStatement s = null;
      ResultSet rs = null;
      Source source = null;
      try {
        conn = DriverManager.getConnection(connectionURL);
        server.getLogger().fine(executeQueryMsg + statement);
        s = conn.prepareStatement(statement);
        s.setString(1, sourceName);
        rs = s.executeQuery();
        while (rs.next()) { // the select statement must guarantee only one row is returned.
          source = resultSetToSource(rs);
        }
      }
      catch (SQLException e) {
        this.logger.info("DB: Error in getSource()" + StackTrace.toString(e));
      }
      finally {
        try {
          rs.close();
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
      return source;
    }
  }

  /** {@inheritDoc} */
  @Override
  public SourceSummary getSourceSummary(String sourceName) {
    if (sourceName == null) {
      // null or non-existent source name
      return null;
    }
    Source baseSource = getSource(sourceName);
    if (baseSource == null) {
      return null;
    }
    SourceSummary summary = new SourceSummary();
    summary.setHref(Source.sourceToUri(sourceName, this.server.getHostName()));
    // Want to go through sensordata for base source, and all subsources recursively
    List<Source> sourceList = getAllNonVirtualSubSources(baseSource);
    XMLGregorianCalendar firstTimestamp = null, lastTimestamp = null;
    Timestamp sqlDataTimestamp = null;
    long dataCount = 0;
    String statement = "SELECT Tstamp FROM SensorData WHERE Source = ? ORDER BY Tstamp";
    Connection conn = null;
    PreparedStatement s = null;
    ResultSet rs = null;

    for (Source subSource : sourceList) {
      String subSourceName = subSource.getName();
      // TODO Seems like there should be a better way to retrieve the first, last and total # of
      // rows without iterating through each one. Maybe I need better SQL-fu

      // Retrieve this Source's SensorData
      try {
        conn = DriverManager.getConnection(connectionURL);
        server.getLogger().fine(executeQueryMsg + statement);
        s = conn.prepareStatement(statement);
        s.setString(1, Source.sourceToUri(subSourceName, this.server));
        rs = s.executeQuery();
        if (rs.next()) {
          sqlDataTimestamp = rs.getTimestamp(1);
          firstTimestamp = Tstamp.makeTimestamp(sqlDataTimestamp);
          lastTimestamp = firstTimestamp;
          dataCount++;
        }
        while (rs.next()) {
          sqlDataTimestamp = rs.getTimestamp(1);
          dataCount++;
        }
        // at end of loop, sqlDataTimestamp is the last timestamp
        if (dataCount > 0) {
          lastTimestamp = Tstamp.makeTimestamp(sqlDataTimestamp);
        }
      }
      catch (SQLException e) {
        this.logger.info("DB: Error in getSourceSummary()" + StackTrace.toString(e));
      }
      finally {
        try {
          rs.close();
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
    }
    summary.setFirstSensorData(firstTimestamp);
    summary.setLastSensorData(lastTimestamp);
    summary.setTotalSensorDatas(dataCount);
    return summary;
  }

  /** {@inheritDoc} */
  @Override
  public boolean storeSource(Source source) {
    if (source == null) {
      return false;
    }
    else {
      Connection conn = null;
      PreparedStatement s = null;
      Marshaller propertiesMarshaller = null;
      Marshaller subSourcesMarshaller = null;
      try {
        propertiesMarshaller = propertiesJAXB.createMarshaller();
        subSourcesMarshaller = subSourcesJAXB.createMarshaller();
      }
      catch (JAXBException e) {
        this.logger.info("Unable to create marshaller" + StackTrace.toString(e));
        return false;
      }
      try {
        conn = DriverManager.getConnection(connectionURL);
        s = conn.prepareStatement("INSERT INTO Source VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        // Order: Name Owner PublicP Virtual Coordinates Location Description SubSources Properties
        // LastMod
        s.setString(1, source.getName());
        s.setString(2, source.getOwner());
        s.setShort(3, booleanToShort(source.isPublic()));
        s.setShort(4, booleanToShort(source.isVirtual()));
        s.setString(5, source.getCoordinates());
        s.setString(6, source.getLocation());
        s.setString(7, source.getDescription());
        if (source.isSetSubSources()) {
          StringWriter writer = new StringWriter();
          subSourcesMarshaller.marshal(source.getSubSources(), writer);
          s.setString(8, writer.toString());
        }
        else {
          s.setString(8, null);
        }
        if (source.isSetProperties()) {
          StringWriter writer = new StringWriter();
          propertiesMarshaller.marshal(source.getProperties(), writer);
          s.setString(9, writer.toString());
        }
        else {
          s.setString(9, null);
        }
        s.setTimestamp(10, new Timestamp(new Date().getTime()));
        s.executeUpdate();
        this.logger.fine("Derby: Inserted Source" + source.getName());
        return true;
      }
      catch (SQLException e) {
        if (DUPLICATE_KEY.equals(e.getSQLState())) {
          this.logger.fine("Derby: Attempted to overwrite Source " + source.getName());
          return false;
        }
        else {
          this.logger.info(derbyError + StackTrace.toString(e));
          return false;
        }
      }
      catch (JAXBException e) {
        this.logger.info("Unable to marshall XML field" + StackTrace.toString(e));
        return false;
      }
      finally {
        try {
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteSource(String sourceName) {
    if (sourceName == null) {
      return false;
    }
    else {
      deleteSensorData(sourceName);
      String statement = "DELETE FROM Source WHERE Name='" + sourceName + "'";
      return deleteResource(statement);
    }
  }

  /** The SQL string for creating the SensorData table. */
  private static final String createSensorDataTableStatement =
      "create table SensorData  " + "(" + " Tstamp TIMESTAMP NOT NULL, "
          + " Tool VARCHAR(128) NOT NULL, " + " Source VARCHAR(256) NOT NULL, "
          + " Properties VARCHAR(32000), " + " LastMod TIMESTAMP NOT NULL, "
          + " PRIMARY KEY (Source, Tstamp) " + ")";

  /** An SQL string to test whether the User table exists and has the correct schema. */
  private static final String testSensorDataTableStatement =
      " UPDATE SensorData SET "
          + " Tstamp = '"
          + new Timestamp(new Date().getTime()).toString()
          + "', "
          + " Tool = 'test-db-tool', "
          + " Source = 'test-db-source', "
          + " Properties = '<Properties><Property><Key>powerGenerated</Key><Value>4.6E7</Value></Property></Properties>', "
          + " LastMod = '" + new Timestamp(new Date().getTime()).toString() + "' " + " WHERE 1=3";

  /** {@inheritDoc} */
  @Override
  public SensorDataIndex getSensorDataIndex(String sourceName) {
    if (sourceName == null) {
      return null;
    }
    else if (getSource(sourceName) == null) {
      // Unknown Source name, therefore no possibility of SensorData
      return null;
    }
    else {
      SensorDataIndex index = new SensorDataIndex();
      String statement =
          "SELECT Tstamp, Tool, Source FROM SensorData WHERE Source = ? ORDER BY Tstamp";
      Connection conn = null;
      PreparedStatement s = null;
      ResultSet rs = null;
      SensorDataRef ref;
      try {
        conn = DriverManager.getConnection(connectionURL);
        server.getLogger().fine(executeQueryMsg + statement);
        s = conn.prepareStatement(statement);
        s.setString(1, Source.sourceToUri(sourceName, this.server));
        rs = s.executeQuery();
        while (rs.next()) {
          Timestamp timestamp = rs.getTimestamp(1);
          String tool = rs.getString(2);
          String sourceUri = rs.getString(3);
          ref = new SensorDataRef(Tstamp.makeTimestamp(timestamp), tool, sourceUri);
          index.getSensorDataRef().add(ref);
        }
      }
      catch (SQLException e) {
        this.logger.info("DB: Error in getSensorDataIndex()" + StackTrace.toString(e));
      }
      finally {
        try {
          rs.close();
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
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
    else if (getSource(sourceName) == null) {
      // Unknown Source name, therefore no possibility of SensorData
      return null;
    }
    else if (startTime.compare(endTime) == DatatypeConstants.GREATER) {
      // startTime > endTime, which is bogus
      throw new DbBadIntervalException(startTime, endTime);
    }
    else {
      SensorDataIndex index = new SensorDataIndex();
      String statement =
          "SELECT Tstamp, Tool, Source FROM SensorData WHERE Source = ? AND "
              + " (Tstamp BETWEEN ? AND ?)" + " ORDER BY Tstamp";
      Connection conn = null;
      PreparedStatement s = null;
      ResultSet rs = null;
      SensorDataRef ref;
      try {
        conn = DriverManager.getConnection(connectionURL);
        server.getLogger().fine(executeQueryMsg + statement);
        s = conn.prepareStatement(statement);
        s.setString(1, Source.sourceToUri(sourceName, this.server));
        s.setTimestamp(2, Tstamp.makeTimestamp(startTime));
        s.setTimestamp(3, Tstamp.makeTimestamp(endTime));
        rs = s.executeQuery();
        while (rs.next()) {
          Timestamp timestamp = rs.getTimestamp(1);
          String tool = rs.getString(2);
          String sourceUri = rs.getString(3);
          ref = new SensorDataRef(Tstamp.makeTimestamp(timestamp), tool, sourceUri);
          index.getSensorDataRef().add(ref);
        }
      }
      catch (SQLException e) {
        this.logger.info("DB: Error in getSensorDataIndex()" + StackTrace.toString(e));
      }
      finally {
        try {
          rs.close();
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
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
      String statement = "SELECT * FROM SensorData WHERE Source = ? AND Tstamp = ?";
      Connection conn = null;
      PreparedStatement s = null;
      ResultSet rs = null;
      boolean hasData = false;
      SensorData data = new SensorData();
      String xmlString;
      try {
        conn = DriverManager.getConnection(connectionURL);
        server.getLogger().fine(executeQueryMsg + statement);
        s = conn.prepareStatement(statement);
        s.setString(1, Source.sourceToUri(sourceName, this.server));
        s.setTimestamp(2, Tstamp.makeTimestamp(timestamp));
        rs = s.executeQuery();
        while (rs.next()) { // the select statement must guarantee only one row is returned.
          hasData = true;
          data.setTimestamp(Tstamp.makeTimestamp(rs.getTimestamp(1)));
          data.setTool(rs.getString(2));
          data.setSource(rs.getString(3));
          xmlString = rs.getString(4);
          if (xmlString != null) {
            try {
              Unmarshaller unmarshaller = propertiesJAXB.createUnmarshaller();
              data.setProperties((Properties) unmarshaller.unmarshal(new StringReader(xmlString)));
            }
            catch (JAXBException e) {
              // Got some XML from DB we can't parse
              this.logger.warning(UNABLE_TO_PARSE_PROPERTY_XML + StackTrace.toString(e));
            }
          }
        }
      }
      catch (SQLException e) {
        this.logger.info("DB: Error in getSensorData()" + StackTrace.toString(e));
      }
      finally {
        try {
          rs.close();
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
      return (hasData) ? data : null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasSensorData(String sourceName, XMLGregorianCalendar timestamp) {
    // Could be made a little faster by doing just the DB query ourselves and not reconstituting
    // the SensorData object, but that's probably an unneeded optimization.
    return (getSensorData(sourceName, timestamp) != null);
  }

  /** {@inheritDoc} */
  @Override
  public boolean storeSensorData(SensorData data) {
    if (data == null) {
      return false;
    }
    else {
      Connection conn = null;
      PreparedStatement s = null;
      Marshaller propertiesMarshaller = null;
      try {
        propertiesMarshaller = propertiesJAXB.createMarshaller();
      }
      catch (JAXBException e) {
        this.logger.info("Unable to create marshaller" + StackTrace.toString(e));
        return false;
      }
      try {
        conn = DriverManager.getConnection(connectionURL);
        s = conn.prepareStatement("INSERT INTO SensorData VALUES (?, ?, ?, ?, ?)");
        // Order: Tstamp Tool Source Properties LastMod
        s.setTimestamp(1, Tstamp.makeTimestamp(data.getTimestamp()));
        s.setString(2, data.getTool());
        s.setString(3, data.getSource());
        if (data.isSetProperties()) {
          StringWriter writer = new StringWriter();
          propertiesMarshaller.marshal(data.getProperties(), writer);
          s.setString(4, writer.toString());
        }
        else {
          s.setString(4, null);
        }
        s.setTimestamp(5, new Timestamp(new Date().getTime()));
        s.executeUpdate();
        this.logger.fine("Derby: Inserted SensorData" + data.getTimestamp());
        return true;
      }
      catch (SQLException e) {
        if (DUPLICATE_KEY.equals(e.getSQLState())) {
          this.logger.fine("Derby: Attempted to overwrite SensorData " + data.getTimestamp());
          return false;
        }
        else {
          this.logger.info(derbyError + StackTrace.toString(e));
          return false;
        }
      }
      catch (JAXBException e) {
        this.logger.info("Unable to marshall XML field" + StackTrace.toString(e));
        return false;
      }
      finally {
        try {
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteSensorData(String sourceName, XMLGregorianCalendar timestamp) {
    boolean succeeded = false;
    if ((sourceName == null) || (timestamp == null)) {
      return false;
    }
    else {
      String sourceUri = Source.sourceToUri(sourceName, this.server.getHostName());
      String statement =
          "DELETE FROM SensorData WHERE Source='" + sourceUri + "' AND Tstamp='"
              + Tstamp.makeTimestamp(timestamp) + "'";
      succeeded = deleteResource(statement);
      return succeeded;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteSensorData(String sourceName) {
    boolean succeeded = false;

    if (sourceName == null) {
      return false;
    }
    else {
      String sourceUri = Source.sourceToUri(sourceName, this.server.getHostName());
      String statement = "DELETE FROM SensorData WHERE Source='" + sourceUri + "'";
      succeeded = deleteResource(statement);
    }
    return succeeded;
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
          new SensorData(Tstamp.makeTimestamp("1700-01-01T00:00:00.000-10:00"), "", "");
      afterSentinel = new SensorData(Tstamp.makeTimestamp("3000-01-01T00:00:00.000-10:00"), "", "");
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
    Source source = getSource(sourceName);
    if (source == null) {
      return null;
    }
    XMLGregorianCalendar dataTimestamp;
    int dataTimestampCompare;

    SensorData data = getSensorData(sourceName, timestamp);
    if (data == null) {
      // TODO Seems like there should be a better way to retrieve the row with timestamps that come
      // just before and just after a given timestamp. Maybe I need better SQL-fu. For now just
      // iterate over all timestamps for the Source
      String statement = "SELECT Tstamp FROM SensorData WHERE Source = ? ORDER BY Tstamp";
      Connection conn = null;
      PreparedStatement s = null;
      ResultSet rs = null;
      try {
        conn = DriverManager.getConnection(connectionURL);
        server.getLogger().fine(executeQueryMsg + statement);
        s = conn.prepareStatement(statement);
        s.setString(1, Source.sourceToUri(sourceName, this.server));
        rs = s.executeQuery();
        // Loop over all SensorData for source
        while (rs.next()) {
          dataTimestamp = Tstamp.makeTimestamp(rs.getTimestamp(1));
          dataTimestampCompare = dataTimestamp.compare(timestamp);
          if (dataTimestampCompare == DatatypeConstants.EQUAL) {
            // There is SensorData for the requested timestamp, but we already checked for this.
            // Thus there is a logic error somewhere
            this.logger
                .warning("Found sensordata that matches timestamp, but after already checked!");
            SensorData tempData = getSensorData(sourceName, dataTimestamp);
            return new SensorDataStraddle(timestamp, tempData, tempData);
          }
          if ((dataTimestamp.compare(beforeData.getTimestamp()) == DatatypeConstants.GREATER)
              && (dataTimestampCompare == DatatypeConstants.LESSER)) {
            // found closer beforeData
            beforeData = getSensorData(sourceName, dataTimestamp);
          }
          else if ((dataTimestamp.compare(afterData.getTimestamp()) == DatatypeConstants.LESSER)
              && (dataTimestampCompare == DatatypeConstants.GREATER)) {
            // found closer afterData
            afterData = getSensorData(sourceName, dataTimestamp);
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
      catch (SQLException e) {
        this.logger.info("DB: Error in getSensorDataStraddle()" + StackTrace.toString(e));
        return null;
      }
      finally {
        try {
          rs.close();
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
    }
    else {
      // There is SensorData for the requested timestamp, so return degenerate
      // SensorDataStraddle
      return new SensorDataStraddle(timestamp, data, data);
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
    Source baseSource = getSource(sourceName);
    if (baseSource == null) {
      return null;
    }
    // Want to go through sensordata for base source, and all subsources recursively
    List<Source> sourceList = getAllNonVirtualSubSources(baseSource);
    List<SensorDataStraddle> straddleList = new ArrayList<SensorDataStraddle>(sourceList.size());
    for (Source subSource : sourceList) {
      String subSourceName = subSource.getName();
      SensorDataStraddle straddle = getSensorDataStraddle(subSourceName, timestamp);
      if (straddle == null) {
        // No straddle for this timestamp on this source, abort
        return null;
      }
      else {
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
  public List<StraddleList> getStraddleLists(String sourceName,
      List<XMLGregorianCalendar> timestampList) {
    if ((sourceName == null) || (timestampList == null)) {
      return null;
    }
    Source baseSource = getSource(sourceName);
    if (baseSource == null) {
      return null;
    }
    // Want to go through sensordata for base source, and all subsources recursively
    List<Source> sourceList = getAllNonVirtualSubSources(baseSource);
    List<StraddleList> masterList = new ArrayList<StraddleList>(sourceList.size());
    List<SensorDataStraddle> straddleList;
    for (Source subSource : sourceList) {
      straddleList = new ArrayList<SensorDataStraddle>(timestampList.size());
      String subSourceName = subSource.getName();
      for (XMLGregorianCalendar timestamp : timestampList) {
        SensorDataStraddle straddle = getSensorDataStraddle(subSourceName, timestamp);
        if (straddle == null) {
          // No straddle for this timestamp on this source, abort
          return null;
        }
        else {
          straddleList.add(straddle);
        }
      }
      if (straddleList.isEmpty()) {
        return null;
      }
      else {
        masterList.add(new StraddleList(subSource, straddleList));
      }
    }
    return masterList;
  }

  /** {@inheritDoc} */
  @Override
  public List<List<SensorDataStraddle>> getSensorDataStraddleListOfLists(String sourceName,
      List<XMLGregorianCalendar> timestampList) {
    List<List<SensorDataStraddle>> masterList = new ArrayList<List<SensorDataStraddle>>();
    if ((sourceName == null) || (timestampList == null)) {
      return null;
    }
    Source baseSource = getSource(sourceName);
    if (baseSource == null) {
      return null;
    }
    // Want to go through sensordata for base source, and all subsources recursively
    List<Source> sourceList = getAllNonVirtualSubSources(baseSource);
    for (Source subSource : sourceList) {
      List<SensorDataStraddle> straddleList = new ArrayList<SensorDataStraddle>();
      String subSourceName = subSource.getName();
      for (XMLGregorianCalendar timestamp : timestampList) {
        SensorDataStraddle straddle = getSensorDataStraddle(subSourceName, timestamp);
        if (straddle == null) {
          // No straddle for this timestamp on this source, abort
          return null;
        }
        else {
          straddleList.add(straddle);
        }
      }
      masterList.add(straddleList);
    }
    if (masterList.isEmpty()) {
      return null;
    }
    else {
      return masterList;
    }
  }

  /** The SQL string for creating the WattDepotUser table. So named because 'User' is reserved. */
  private static final String createUserTableStatement =
      "create table WattDepotUser  " + "(" + " Username VARCHAR(128) NOT NULL, "
          + " Password VARCHAR(128) NOT NULL, " + " Admin SMALLINT NOT NULL, "
          + " Properties VARCHAR(32000), " + " LastMod TIMESTAMP NOT NULL, "
          + " PRIMARY KEY (Username) " + ")";

  /** An SQL string to test whether the User table exists and has the correct schema. */
  private static final String testUserTableStatement =
      " UPDATE WattDepotUser SET "
          + " Username = 'TestEmail@foo.com', "
          + " Password = 'changeme', "
          + " Admin = 0, "
          + " Properties = '<Properties><Property><Key>awesomeness</Key><Value>total</Value></Property></Properties>', "
          + " LastMod = '" + new Timestamp(new Date().getTime()).toString() + "' " + " WHERE 1=3";

  /** {@inheritDoc} */
  @Override
  public UserIndex getUsers() {
    UserIndex index = new UserIndex();
    String statement = "SELECT Username FROM WattDepotUser ORDER BY Username";
    Connection conn = null;
    PreparedStatement s = null;
    ResultSet rs = null;
    try {
      conn = DriverManager.getConnection(connectionURL);
      server.getLogger().fine(executeQueryMsg + statement);
      s = conn.prepareStatement(statement);
      rs = s.executeQuery();
      while (rs.next()) {
        index.getUserRef().add(new UserRef(rs.getString("Username"), this.server));
      }
    }
    catch (SQLException e) {
      this.logger.info("DB: Error in getUsers()" + StackTrace.toString(e));
    }
    finally {
      try {
        rs.close();
        s.close();
        conn.close();
      }
      catch (SQLException e) {
        this.logger.warning(errorClosingMsg + StackTrace.toString(e));
      }
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
      String statement = "SELECT * FROM WattDepotUser WHERE Username = ?";
      Connection conn = null;
      PreparedStatement s = null;
      ResultSet rs = null;
      boolean hasData = false;
      User user = new User();
      try {
        conn = DriverManager.getConnection(connectionURL);
        server.getLogger().fine(executeQueryMsg + statement);
        s = conn.prepareStatement(statement);
        s.setString(1, username);
        rs = s.executeQuery();
        while (rs.next()) { // the select statement must guarantee only one row is returned.
          hasData = true;
          user.setEmail(rs.getString("Username"));
          user.setPassword(rs.getString("Password"));
          user.setAdmin(rs.getBoolean("Admin"));
          String xmlString = rs.getString("Properties");
          if (xmlString != null) {
            try {
              Unmarshaller unmarshaller = propertiesJAXB.createUnmarshaller();
              user.setProperties((Properties) unmarshaller.unmarshal(new StringReader(xmlString)));
            }
            catch (JAXBException e) {
              // Got some XML from DB we can't parse
              this.logger.warning(UNABLE_TO_PARSE_PROPERTY_XML + StackTrace.toString(e));
            }
          }
        }
      }
      catch (SQLException e) {
        this.logger.info("DB: Error in getUser()" + StackTrace.toString(e));
      }
      finally {
        try {
          rs.close();
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
      return (hasData) ? user : null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean storeUser(User user) {
    if (user == null) {
      return false;
    }
    else {
      Connection conn = null;
      PreparedStatement s = null;
      Marshaller marshaller = null;
      try {
        marshaller = propertiesJAXB.createMarshaller();
      }
      catch (JAXBException e) {
        this.logger.info("Unable to create marshaller" + StackTrace.toString(e));
        return false;
      }
      try {
        conn = DriverManager.getConnection(connectionURL);
        s = conn.prepareStatement("INSERT INTO WattDepotUser VALUES (?, ?, ?, ?, ?)");
        // Order: Username Password Admin Properties LastMod
        s.setString(1, user.getEmail());
        s.setString(2, user.getPassword());
        s.setShort(3, booleanToShort(user.isAdmin()));
        if (user.isSetProperties()) {
          StringWriter writer = new StringWriter();
          marshaller.marshal(user.getProperties(), writer);
          s.setString(4, writer.toString());
        }
        else {
          s.setString(4, null);
        }
        s.setTimestamp(5, new Timestamp(new Date().getTime()));
        s.executeUpdate();
        this.logger.fine("Derby: Inserted User" + user.getEmail());
        return true;
      }
      catch (SQLException e) {
        if (DUPLICATE_KEY.equals(e.getSQLState())) {
          this.logger.fine("Derby: Attempted to overwrite User " + user.getEmail());
          return false;
        }
        else {
          this.logger.info(derbyError + StackTrace.toString(e));
          return false;
        }
      }
      catch (JAXBException e) {
        this.logger.info("Unable to marshall Properties" + StackTrace.toString(e));
        return false;
      }
      finally {
        try {
          s.close();
          conn.close();
        }
        catch (SQLException e) {
          this.logger.warning(errorClosingMsg + StackTrace.toString(e));
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean deleteUser(String username) {
    if (username == null) {
      return false;
    }
    else {
      String statement = "DELETE FROM WattDepotUser WHERE Username='" + username + "'";
      return deleteResource(statement);
      // TODO add code to delete sources and sensordata owned by the user
    }
  }

  /**
   * Deletes the resource, given the SQL statement to perform the delete.
   * 
   * @param statement The SQL delete statement.
   * @return True if resource was successfully deleted, false otherwise.
   */
  private boolean deleteResource(String statement) {
    Connection conn = null;
    PreparedStatement s = null;
    boolean succeeded = false;

    try {
      conn = DriverManager.getConnection(connectionURL);
      server.getLogger().fine("Derby: " + statement);
      s = conn.prepareStatement(statement);
      int rowCount = s.executeUpdate();
      // actually deleted something
      if (rowCount >= 1) {
        succeeded = true;
      }
      else {
        // didn't delete anything (maybe resource didn't exist?)
        return false;
      }
    }
    catch (SQLException e) {
      this.logger.info("Derby: Error in deleteResource()" + StackTrace.toString(e));
      succeeded = false;
    }
    finally {
      try {
        s.close();
        conn.close();
      }
      catch (SQLException e) {
        this.logger.warning(errorClosingMsg + StackTrace.toString(e));
      }
    }
    return succeeded;
  }

  /**
   * Converts a boolean into 1 (for true), or 0 (for false). Useful because Derby does not support
   * the boolean SQL type, so recommended procedure is to use a SHORTINT column type with value 1 or
   * 0.
   * 
   * @param value The boolean value.
   * @return 1 if value is true, 0 if value is false.
   */
  private short booleanToShort(boolean value) {
    return value ? (short) 1 : (short) 0;
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

  @Override
  public boolean wipeData() {
    try {
      wipeTables();
      return true;
    }
    catch (SQLException e) {
      return false;
    }
  }
}
