//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-833 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2009.08.17 at 01:54:50 PM HST 
//

package org.wattdepot.resource.sensordata.jaxb;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

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
 *       &lt;sequence>
 *         &lt;element ref="{}SensorDataRef" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "sensorDataRef" })
@XmlRootElement(name = "SensorDataIndex")
public class SensorDataIndex implements Serializable {

  private final static long serialVersionUID = 12343L;
  @XmlElement(name = "SensorDataRef")
  protected List<SensorDataRef> sensorDataRef;

  /**
   * Default no-argument constructor, apparently needed by JAXB. Don't use this, use the one with
   * all the parameters.
   */
  public SensorDataIndex() {
    // Apparently needed by JAXB
  }

  /**
   * Creates a SourceIndex with the requested capacity.
   */
  public SensorDataIndex(int capacity) {
    this.sensorDataRef = new ArrayList<SensorDataRef>(capacity);
  }

  /**
   * Gets the value of the sensorDataRef property.
   * 
   * <p>
   * This accessor method returns a reference to the live list, not a snapshot. Therefore any
   * modification you make to the returned list will be present inside the JAXB object. This is why
   * there is not a <CODE>set</CODE> method for the sensorDataRef property.
   * 
   * <p>
   * For example, to add a new item, do as follows:
   * 
   * <pre>
   * getSensorDataRef().add(newItem);
   * </pre>
   * 
   * 
   * <p>
   * Objects of the following type(s) are allowed in the list {@link SensorDataRef }
   * 
   * 
   */
  public List<SensorDataRef> getSensorDataRef() {
    if (sensorDataRef == null) {
      sensorDataRef = new ArrayList<SensorDataRef>();
    }
    return this.sensorDataRef;
  }

  public boolean isSetSensorDataRef() {
    return ((this.sensorDataRef != null) && (!this.sensorDataRef.isEmpty()));
  }

  public void unsetSensorDataRef() {
    this.sensorDataRef = null;
  }

}
