//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-833 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2009.08.31 at 03:06:32 PM HST 
//

package org.wattdepot.resource.source.jaxb;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import org.wattdepot.server.Server;

/**
 * <p>
 * Java class for anonymous complex type.
 * 
 * <p>
 * The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;attribute ref="{}Name use="required""/>
 *       &lt;attribute ref="{}Owner use="required""/>
 *       &lt;attribute ref="{}Public use="required""/>
 *       &lt;attribute ref="{}Virtual use="required""/>
 *       &lt;attribute ref="{}Coordinates use="required""/>
 *       &lt;attribute ref="{}Location use="required""/>
 *       &lt;attribute ref="{}Description use="required""/>
 *       &lt;attribute ref="{}Href use="required""/>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
@XmlRootElement(name = "SourceRef")
public class SourceRef implements Serializable, Comparable<SourceRef> {

  private final static long serialVersionUID = 12343L;
  @XmlAttribute(name = "Name", required = true)
  protected String name;
  @XmlAttribute(name = "Owner", required = true)
  @XmlSchemaType(name = "anyURI")
  protected String owner;
  @XmlAttribute(name = "Public", required = true)
  protected boolean _public;
  @XmlAttribute(name = "Virtual", required = true)
  protected boolean virtual;
  @XmlAttribute(name = "Coordinates", required = true)
  protected String coordinates;
  @XmlAttribute(name = "Location", required = true)
  protected String location;
  @XmlAttribute(name = "Description", required = true)
  protected String description;
  @XmlAttribute(name = "Href", required = true)
  @XmlSchemaType(name = "anyURI")
  protected String href;

  /**
   * Default no-argument constructor, apparently needed by JAXB. Don't use this, use the one with
   * all the parameters.
   */
  public SourceRef() {
    // Apparently needed by JAXB
  }

  /**
   * Creates a SourceRef object from a Source object. The Server argument is required to build the
   * URI in the SourceRef pointing to the full Source resource.
   * 
   * @param source The Source to build the SourceRef from.
   * @param server The Server where the Source is located.
   * @return The new SourceRef object.
   */
  public SourceRef(Source source, Server server) {
    this(source, server.getHostName() + Server.SOURCES_URI + "/" + source.getName());
  }

  /**
   * Creates a SourceRef object from a Source object using the provided URI for the SourceRef
   * pointing to the full Source resource. Needs to be kept up to date with any changes to the
   * schema, which is bogus.
   * 
   * @param source The Source to build the SourceRef from.
   * @param uri The URI where the Source is located.
   * @return The new SourceRef object.
   */
  public SourceRef(Source source, String uri) {
    this(source.getName(), source.getOwner(), source.isPublic(), source.isVirtual(), source
        .getCoordinates(), source.getLocation(), source.getDescription(), uri);
  }

  /**
   * Creates a SourceRef object from the given parameters. Needs to be kept up to date with any
   * changes to the schema, which is bogus.
   * 
   * @param name The name of the Source.
   * @param owner The owner of the Source.
   * @param publicp Whether the Source is public.
   * @param virtualp Whether the Source is virtual.
   * @param coordinates The coordinates of the Source.
   * @param location The location of the Source.
   * @param description The description of the Source.
   * @param uri The URI where the Source is located.
   * @return The new SourceRef object.
   */
  public SourceRef(String name, String owner, boolean publicp, boolean virtualp,
      String coordinates, String location, String description, String uri) {
    this.name = name;
    this.owner = owner;
    this._public = publicp;
    this.virtual = virtualp;
    this.coordinates = coordinates;
    this.location = location;
    this.description = description;
    this.href = uri;
  }

  /**
   * Gets the value of the name property.
   * 
   * @return possible object is {@link String }
   * 
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the value of the name property.
   * 
   * @param value allowed object is {@link String }
   * 
   */
  public void setName(String value) {
    this.name = value;
  }

  public boolean isSetName() {
    return (this.name != null);
  }

  /**
   * Gets the value of the owner property.
   * 
   * @return possible object is {@link String }
   * 
   */
  public String getOwner() {
    return owner;
  }

  /**
   * Sets the value of the owner property.
   * 
   * @param value allowed object is {@link String }
   * 
   */
  public void setOwner(String value) {
    this.owner = value;
  }

  public boolean isSetOwner() {
    return (this.owner != null);
  }

  /**
   * Gets the value of the public property.
   * 
   */
  public boolean isPublic() {
    return _public;
  }

  /**
   * Sets the value of the public property.
   * 
   */
  public void setPublic(boolean value) {
    this._public = value;
  }

  public boolean isSetPublic() {
    return true;
  }

  /**
   * Gets the value of the virtual property.
   * 
   */
  public boolean isVirtual() {
    return virtual;
  }

  /**
   * Sets the value of the virtual property.
   * 
   */
  public void setVirtual(boolean value) {
    this.virtual = value;
  }

  public boolean isSetVirtual() {
    return true;
  }

  /**
   * Gets the value of the coordinates property.
   * 
   * @return possible object is {@link String }
   * 
   */
  public String getCoordinates() {
    return coordinates;
  }

  /**
   * Sets the value of the coordinates property.
   * 
   * @param value allowed object is {@link String }
   * 
   */
  public void setCoordinates(String value) {
    this.coordinates = value;
  }

  public boolean isSetCoordinates() {
    return (this.coordinates != null);
  }

  /**
   * Gets the value of the location property.
   * 
   * @return possible object is {@link String }
   * 
   */
  public String getLocation() {
    return location;
  }

  /**
   * Sets the value of the location property.
   * 
   * @param value allowed object is {@link String }
   * 
   */
  public void setLocation(String value) {
    this.location = value;
  }

  public boolean isSetLocation() {
    return (this.location != null);
  }

  /**
   * Gets the value of the description property.
   * 
   * @return possible object is {@link String }
   * 
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the value of the description property.
   * 
   * @param value allowed object is {@link String }
   * 
   */
  public void setDescription(String value) {
    this.description = value;
  }

  public boolean isSetDescription() {
    return (this.description != null);
  }

  /**
   * Gets the value of the href property.
   * 
   * @return possible object is {@link String }
   * 
   */
  public String getHref() {
    return href;
  }

  /**
   * Sets the value of the href property.
   * 
   * @param value allowed object is {@link String }
   * 
   */
  public void setHref(String value) {
    this.href = value;
  }

  public boolean isSetHref() {
    return (this.href != null);
  }

  // Broke down and added these manually to the generated code. It would be better if they were
  // automatically generated via XJC plugins, but that required a bunch of dependencies that I
  // was unwilling to deal with right now. If the schema files change, this code will be blown
  // away, so there are unit tests that confirm that equals and hashCode work to guard against
  // that.

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (_public ? 1231 : 1237);
    result = prime * result + ((coordinates == null) ? 0 : coordinates.hashCode());
    result = prime * result + ((description == null) ? 0 : description.hashCode());
    result = prime * result + ((href == null) ? 0 : href.hashCode());
    result = prime * result + ((location == null) ? 0 : location.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((owner == null) ? 0 : owner.hashCode());
    result = prime * result + (virtual ? 1231 : 1237);
    return result;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    SourceRef other = (SourceRef) obj;
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    }
    else if (!name.equals(other.name)) {
      return false;
    }
    if (_public != other._public) {
      return false;
    }
    if (coordinates == null) {
      if (other.coordinates != null) {
        return false;
      }
    }
    else if (!coordinates.equals(other.coordinates)) {
      return false;
    }
    if (description == null) {
      if (other.description != null) {
        return false;
      }
    }
    else if (!description.equals(other.description)) {
      return false;
    }
    if (href == null) {
      if (other.href != null) {
        return false;
      }
    }
    else if (!href.equals(other.href)) {
      return false;
    }
    if (location == null) {
      if (other.location != null) {
        return false;
      }
    }
    else if (!location.equals(other.location)) {
      return false;
    }
    if (owner == null) {
      if (other.owner != null) {
        return false;
      }
    }
    else if (!owner.equals(other.owner)) {
      return false;
    }
    if (virtual != other.virtual) {
      return false;
    }
    return true;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Comparable<T>#compareTo(java.lang.Comparable<T>)
   */
  @Override
  public int compareTo(SourceRef o) {
    // if o is null, throw NullPointerException, per Comparable JavaDoc
    if (o == null) {
      throw new NullPointerException("Tried to compare SourceRef with null");
    }
    if (o.equals(this)) {
      return 0;
    }
    // move on to the other fields for comparison
    int comparison;
    comparison = name.compareTo(o.getName());
    if (comparison != 0) {
      // names differ, so just return the comparison value
      return comparison;
    }
    // names are the same, so check owner field
    comparison = owner.compareTo(o.getOwner());
    if (comparison != 0) {
      // owners differ, so just return the comparison value
      return comparison;
    }
    // Check public flag, ordering true before false
    if (_public && !o.isPublic()) {
      return -1;
    }
    else if (!_public && o.isPublic()) {
      return 1;
    }
    // Check virtual flag, ordering true before false
    if (virtual && !o.isVirtual()) {
      return -1;
    }
    else if (!virtual && o.isVirtual()) {
      return 1;
    }
    comparison = coordinates.compareTo(o.getCoordinates());
    if (comparison != 0) {
      // coordinates differ, so just return the comparison value
      return comparison;
    }
    comparison = location.compareTo(o.getLocation());
    if (comparison != 0) {
      // locations differ, so just return the comparison value
      return comparison;
    }
    comparison = description.compareTo(o.getDescription());
    if (comparison != 0) {
      // description differ, so just return the comparison value
      return comparison;
    }
    comparison = href.compareTo(o.getHref());
    if (comparison != 0) {
      // hrefs differ, so just return the comparison value
      return comparison;
    }
    // Should never get here, since testing every field individually should have same result as
    // equals() which we do first. Anyway, give up and say they are the same.
    return 0;
  }
  
  /**
   * Determines if the subset of information in a SourceRef is equal to particular Source object.
   * Note that only the final segment of the href field of the SourceRef is compared to the Source
   * object, as the Source object does not contain its own URI. Thus if the SourceRef was from a
   * different server than the Source object, this test would return true even though the SourceRef
   * points to a different copy of this Source object.
   * 
   * @param ref The SourceRef to be compared.
   * @param source The Source to be compared.
   * @return True if all the fields in the SourceRef correspond to the same fields in the Source
   */
  public boolean equalsSource(Source source) {
    String hrefSourceName = this.getHref().substring(this.getHref().lastIndexOf('/') + 1);

    return (this.getName().equals(source.getName()) && (this.getOwner().equals(source.getOwner()))
        && (this.isPublic() == source.isPublic()) && (this.isVirtual() == source.isVirtual())
        && (this.getCoordinates().equals(source.getCoordinates()))
        && (this.getLocation().equals(source.getLocation()))
        && (this.getDescription().equals(source.getDescription())) && (source.getName()
        .equals(hrefSourceName)));
  }
}
