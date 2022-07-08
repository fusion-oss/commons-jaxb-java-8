package com.scoperetail.commons.jaxb;

/*-
 * *****
 * commons-jaxb
 * -----
 * Copyright (C) 2018 - 2022 Scope Retail Systems Inc.
 * -----
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * =====
 */

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

import com.sun.xml.bind.marshaller.DataWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JaxbUtil {


  private static final Map<String, JAXBContext> contextMap = new HashMap<>(5);

  private JaxbUtil() {}

  /**
   * Validate the message based on XML schema
   *
   * @param message
   * @param schema
   * @return validationStatus
   */
  public static boolean isValidMessage(final String message, final Schema schema) {
    log.info("Validating inbound message::");
    Boolean validationStatus = Boolean.FALSE;
    try {
      log.debug("Validating inbound message");
      final Validator validator =
          Optional.ofNullable(schema)
              .orElseThrow(() -> new IllegalArgumentException("Schema object not provided."))
              .newValidator();
      validator.validate(
          new StreamSource(
              new ByteArrayInputStream(
                  Optional.ofNullable(message).orElseThrow(SAXException::new).getBytes(UTF_8))));
      log.debug("Validation Successful");
      validationStatus = Boolean.TRUE;
      log.info("Validation Succssful::");
    } catch (SAXException | IOException e) {
      log.error("Inbound message validation Failed::{}", e);
    }
    return validationStatus;
  }

  /**
   * Converts XML message to object based on schema
   *
   * @param messageOpt
   * @param schemaOpt
   * @param clazz
   * @return
   * @throws SAXException
   * @throws JAXBException
   */
  public static final <T> T unmarshal(
      final Optional<String> messageOpt, final Optional<Schema> schemaOpt, final Class<T> clazz)
      throws SAXException, JAXBException {
    log.debug("Unmarshalling inbound message::");
    final JAXBContext jaxbContext = getCachedContext(clazz);
    final StringReader sr = new StringReader(messageOpt.orElseThrow(SAXException::new));
    final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
    unmarshaller.setSchema(
        schemaOpt.orElseThrow(() -> new IllegalArgumentException("Schema object not provided.")));
    final JAXBElement<T> root = unmarshaller.unmarshal(new StreamSource(sr), clazz);
    log.debug("Unmarshalling of message is completed");
    return root.getValue();
  }

  /**
   * Cache the jaxbContext in a hashmap to reduce metaspace footprint.
   *
   * @param clazz
   * @return jaxbContext
   * @throws JAXBException
   */
  private static JAXBContext getCachedContext(final Class clazz) throws JAXBException {
    final JAXBContext jaxbContext;
    if (contextMap.containsKey(clazz.getName())) {
      jaxbContext = contextMap.get(clazz.getName());
    } else {
      jaxbContext = JAXBContext.newInstance(clazz);
      contextMap.put(clazz.getName(), jaxbContext);
    }
    return jaxbContext;
  }

  /**
   * Converts object to XML message.
   *
   * @param clazz
   * @param element
   * @return
   * @throws JAXBException
   */
  public static final <T> Optional<String> marshal(final Class<T> clazz, final Object element)
      throws JAXBException {
    log.debug("Marshalling Object to message::");
    final StringWriter sw = new StringWriter();
    final JAXBContext jaxbContext = getCachedContext(clazz);
    final Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.marshal(element, sw);
    log.debug("Marshalling Object to message is completed");
    return Optional.of(sw.toString());
  }

  /**
   * General purpose marshal method to marshal XML with CDATA
   *
   * @param clazz
   * @param element
   * @return
   * @throws JAXBException
   */
  public static final <T> Optional<String> marshalForCDATA(
      final Class<T> clazz, final Object element) throws JAXBException {
    log.debug("Marshalling Object to message::");
    final JAXBContext jaxbContext = getCachedContext(clazz);
    final Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(stringWriter);
    final DataWriter dataWriter =
        new DataWriter(
            printWriter, "UTF-8", (buf, start, len, b, out) -> out.write(buf, start, len));
    marshaller.marshal(element, dataWriter);
    log.debug("Marshalling Object to message is completed");
    return Optional.of(stringWriter.toString());
  }
}
