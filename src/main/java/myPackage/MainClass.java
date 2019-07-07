package myPackage;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.mapper.MappingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;


public class MainClass {

  private final static ObjectMapper objectMapper = new ObjectMapper();
  public static MainClass obj = null;

  private MainClass() {
  }

  public static void main(String[] args) throws IOException {
    Args obj = new Args();
    JCommander commander = JCommander.newBuilder()
        .addObject(obj)
        .build();
    commander.parse(args);
    System.out.println(getInstance().entryPoint(obj.getCrosscorePath(), obj.getFilterPath()));
  }

  public static MainClass getInstance() {
    if (obj == null) {
      synchronized (MainClass.class) {
        if (obj == null) {
          obj = new MainClass();
        }
      }
    }
    return obj;
  }

  private void setJsonPointerValue(ObjectNode rootNode, JsonPointer pointer, JsonNode value) {
    JsonPointer parentPointer = pointer.head();
    JsonNode parentNode = rootNode.at(parentPointer);
    String fieldName = pointer.last().toString().substring(1);
    if (parentNode.isMissingNode() || parentNode.isNull()) {
      parentNode = StringUtils.isNumeric(fieldName) ? objectMapper.createArrayNode()
          : objectMapper.createObjectNode();
      setJsonPointerValue(rootNode, parentPointer, parentNode);
    }
    if (parentNode.isArray()) {
      ArrayNode arrayNode = (ArrayNode) parentNode;
      int index = Integer.parseInt(fieldName);
      IntStream.rangeClosed(arrayNode.size(), index).forEach(i -> arrayNode.addNull());
      arrayNode.set(index, value);
    } else if (parentNode.isObject()) {
      ((ObjectNode) parentNode).set(fieldName, value);
    } else {
      throw new IllegalArgumentException("`" + fieldName + "` can't be set for parent node `"
          + parentPointer + "` because parent is not a container but " + parentNode.getNodeType()
          .name());
    }
  }

  private String getJsonFromFile(String path) throws IOException {
    if (StringUtils.trimToNull(path) != null) {

      return new String(Files.readAllBytes(Paths.get(path)));
    } else {
      return StringUtils.EMPTY;
    }
  }

  public String entryPoint(String crosscorePath, String filterPath) throws IOException {

    POJO pojo = objectMapper.readValue(getJsonFromFile(filterPath), POJO.class);
    final ObjectNode rootNode = objectMapper.createObjectNode();
    Map mapCrosscore = objectMapper.readValue(getJsonFromFile(crosscorePath), Map.class);
    List<Element> elementList = Optional.ofNullable(pojo.getElementList())
        .filter(elements -> !elements.isEmpty())
        .orElseThrow(() -> new MappingException("Empty list for conversion"));
    elementList
        .forEach(element -> {
          ArrayList<JsonNode> list = new ArrayList<>();
          if (Optional.ofNullable(element.getConcat())
              .filter(concat -> !concat.isEmpty())
              .isPresent()) {
            element.getConcat().forEach(input -> {
              if (input.contains("filter")) {

                Entry<String, String> entry = getFilterMap(pojo)
                    .stream()
                    .filter(entries -> input.contains(entries.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new MappingException("The required filter is not present"));
                list.add(parseInput(mapCrosscore, input, entry, element, pojo.getChangeMap()));

              } else {
                list.add(parseInput(mapCrosscore, input, element,
                    pojo.getChangeMap()));
              }
            });

            setJsonPointerValue(rootNode, JsonPointer.compile(element.getMapping()),
                new TextNode(list.get(0).textValue() + "-" + list.get(1).textValue()));
          } else if (Optional.ofNullable(element.getInput())
              .isPresent()) {
            if (element.getInput().contains("filter")) {
              getFilterMap(pojo)
                  .stream()
                  .filter(entry -> element.getInput().contains(entry.getKey()))
                  .forEach(
                      entry -> {
                        setJsonPointerValue(rootNode,
                            JsonPointer.compile(element.getMapping()),
                            parseInput(mapCrosscore, element.getInput(), entry, element,
                                pojo.getChangeMap()));
                      });

            } else {
               setJsonPointerValue(rootNode, JsonPointer.compile(element.getMapping()),
                  parseInput(mapCrosscore, element.getInput(), element, pojo.getChangeMap()));

            }
          } else {
            if (Optional.ofNullable(element.getConstant())
                .isPresent()) {
              setJsonPointerValue(rootNode, JsonPointer.compile(element.getMapping()),
                  new TextNode(element.getConstant()));
            }
          }
        });
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
  }

  private Set<Entry<String, String>> getFilterMap(POJO pojo) {
    return Optional
        .ofNullable(pojo.getFilterMap())
        .filter(stringStringMap -> !stringStringMap.isEmpty())
        .map(Map::entrySet)
        .orElseThrow(
            () -> new MappingException("Empty filter map while input requires filter"));
  }

  private JsonNode parseInput(Map<String, Object> mapcrosscore, String input,
      Entry<String, String> entry, Element element, Map<String, String> mapChange) {
    JSONArray jsonArray = JsonPath.parse(mapcrosscore).read(entry.getValue());
    if (Optional.ofNullable(jsonArray.get(0)).isPresent()) {
      String str = (String) jsonArray.get(0);
      String input1 = input.replaceAll(entry.getKey(), "" + str + "");
      jsonArray = JsonPath.parse(mapcrosscore).read(input1);
      Optional<Object> read = Optional.ofNullable(jsonArray.get(0));
      return getJsonNode(element, mapChange, read);
    }
    return null;
  }

  private JsonNode getJsonNode(Element element, Map<String, String> mapChange,
      Optional<Object> read) {
    JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;
    if (read.isPresent()) {
      Object o = read.get();
      if (o instanceof Map) {
        return jsonNodeFactory.pojoNode(o);
      } else if (o instanceof Collection) {
        return jsonNodeFactory.textNode(o.toString());
      } else if (o instanceof Number) {
        return jsonNodeFactory.numberNode(((Number) o).doubleValue());
      }

      String temp = read.get().toString();
      return jsonNodeFactory.textNode(Optional.ofNullable(element.getFlag())
          .map(StringUtils::trimToNull)
          .map(s1 -> checkTrueOrFalse(temp, mapChange))
          .orElse(temp));

    } else {
      if (Optional.ofNullable(element.getDefaultValue())
          .isPresent()) {
        return jsonNodeFactory.textNode(element.getDefaultValue());
      } else if (element.getMandatory()) {
        throw new MappingException("error while converting");
      }
    }
    return null;
  }

  private JsonNode parseInput(Map<String, Object> mapcrosscore, String input, Element element,
      Map<String, String> mapChange) {
    Optional<Object> read = Optional.ofNullable(JsonPath.parse(mapcrosscore).read(input));
    return getJsonNode(element, mapChange, read);
  }


  private String checkTrueOrFalse(String temp, Map<String, String> mapChange) {
    Set<Entry<String, String>> entrySet = Optional.ofNullable(mapChange)
        .filter(stringStringMap -> !stringStringMap.isEmpty())
        .map(Map::entrySet)
        .orElseThrow(() -> new MappingException("Change map list is empty"));
    for (Iterator<Entry<String, String>> iterator = entrySet.iterator();
        iterator.hasNext(); ) {
      Entry<String, String> entry = iterator.next();
      if (temp.equals(entry.getKey())) {
        temp = entry.getValue();
      }
    }

    return temp;

  }
}