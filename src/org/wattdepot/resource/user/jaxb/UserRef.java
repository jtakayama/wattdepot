//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-833 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2009.08.17 at 01:54:53 PM HST 
//

package org.wattdepot.resource.user.jaxb;

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
 *       &lt;attribute ref="{}Email use="required""/>
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
@XmlRootElement(name = "UserRef")
public class UserRef implements Serializable, Comparable<UserRef> {

  private final static long serialVersionUID = 12343L;
  @XmlAttribute(name = "Email", required = true)
  protected String email;
  @XmlAttribute(name = "Href", required = true)
  @XmlSchemaType(name = "anyURI")
  protected String href;

  /**
   * Default no-argument constructor, apparently needed by JAXB. Don't use this, use the one with
   * all the parameters.
   */
  public UserRef() {
    // Apparently needed by JAXB
  }

  /**
   * Returns a new UserRef object with the provided parameters. Needs to be kept up to date with any
   * changes to the UserRef schema, which is bogus.
   * 
   * @param username The username for the UserRef.
   * @param uri The URI where the User is located.
   */
  public UserRef(String username, String uri) {
    this.email = username;
    this.href = uri;
  }

  /**
   * Creates a UserRef object from a User object. The Server argument is required to build the URI
   * in the UserRef pointing to the full User resource. Needs to be kept up to date with any changes
   * to the User or UserRef schemas, which is bogus.
   * 
   * @param user The User to build the UserRef from.
   * @param server The Server where the User is located.
   */
  public UserRef(User user, Server server) {
    this(user.getEmail(), server.getHostName() + Server.USERS_URI + "/" + user.getEmail());
  }

  /**
   * Gets the value of the email property.
   * 
   * @return possible object is {@link String }
   * 
   */
  public String getEmail() {
    return email;
  }

  /**
   * Sets the value of the email property.
   * 
   * @param value allowed object is {@link String }
   * 
   */
  public void setEmail(String value) {
    this.email = value;
  }

  public boolean isSetEmail() {
    return (this.email != null);
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

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((email == null) ? 0 : email.hashCode());
    result = prime * result + ((href == null) ? 0 : href.hashCode());
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
    UserRef other = (UserRef) obj;
    if (email == null) {
      if (other.email != null) {
        return false;
      }
    }
    else if (!email.equals(other.email)) {
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
    return true;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "UserRef [email=" + email + ", href=" + href + "]";
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Comparable#compareTo()
   */
  @Override
  public int compareTo(UserRef o) {
    // if o is null, throw NullPointerException, per Comparable JavaDoc
    if (o == null) {
      throw new NullPointerException("Tried to compare UserRef with null");
    }
    if (o.equals(this)) {
      return 0;
    }
    int comparison;
    comparison = email.compareTo(o.getEmail());
    if (comparison != 0) {
      // users differ, so just return the comparison value
      return comparison;
    }
    // sources are the same, so check href field
    comparison = href.compareTo(o.getHref());
    if (comparison != 0) {
      // tools differ, so just return the comparison value
      return comparison;
    }
    // something must be incomparable, since we tested equals at the start, yet we have
    // found all the other fields to be equal. Just give up and say they are the same.
    return 0;
  }

  /**
   * Determines if the subset of user information in a UserRef is equal to particular User object.
   * Note that only the final segment of the href field of the UserRef is compared to the User
   * object, as the User object does not contain its own URI. Thus if the UserRef was from a
   * different server than the User object, this test would return true even though the UserRef
   * points to a different copy of this User object.
   * 
   * @param user The User to be compared.
   * @return True if all the fields in the UserRef correspond to the User
   */
  public boolean equalsUser(User user) {
    String hrefUsername = this.getHref().substring(this.getHref().lastIndexOf('/') + 1);

    return (this.getEmail().equals(user.getEmail()) && (user.getEmail().equals(hrefUsername)));
  }

}
