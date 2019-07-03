package myPackage;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.mapper.MappingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import lombok.val;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntryController {

  private final static ObjectMapper objectMapper = new ObjectMapper();

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

  @RequestMapping(method = RequestMethod.POST, value = "/path")
  public String entryPoint(@RequestPart("crosscore") Map mapCrosscore,
      @RequestPart("filter") POJO pojo) throws IOException {

    final ObjectNode rootNode = objectMapper.createObjectNode();
    List<Element> elementList = Optional.ofNullable(pojo.getElementList())
        .filter(elements -> !elements.isEmpty())
        .orElseThrow(() -> new MappingException("Empty list for conversion"));
    elementList
        .forEach(element -> {
          ArrayList<String> list = new ArrayList<>();
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
                new TextNode(list.get(0) + "-" + list.get(1)));
          } else if (Optional.ofNullable(element.getInput())
              .isPresent()) {
            if (element.getInput().contains("filter")) {
              getFilterMap(pojo)
                  .stream()
                  .filter(entry -> element.getInput().contains(entry.getKey()))
                  .forEach(
                      entry -> setJsonPointerValue(rootNode,
                          JsonPointer.compile(element.getMapping()),
                          new TextNode(parseInput(mapCrosscore, element.getInput(), entry,
                              element, pojo.getChangeMap()))));

            } else {
              setJsonPointerValue(rootNode, JsonPointer.compile(element.getMapping()), new TextNode(
                  parseInput(mapCrosscore, element.getInput(), element,
                      pojo.getChangeMap())));
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

  private String parseInput(Map<String, Object> mapcrosscore, String input,
      Entry<String, String> entry, Element element, Map<String, String> mapChange) {
    JSONArray jsonArray = JsonPath.parse(mapcrosscore).read(entry.getValue());
    if (Optional.ofNullable(jsonArray.get(0)).isPresent()) {
      String str = (String) jsonArray.get(0);
      String input1 = input.replaceAll(entry.getKey(), "" + str + "");
      jsonArray = JsonPath.parse(mapcrosscore).read(input1);
      String var = (String) (jsonArray.get(0));
      return Optional.ofNullable(element.getFlag())
          .map(StringUtils::trimToNull)
          .map(s1 -> checkTrueOrFalse(var, mapChange))
          .orElse(var);
    } else {
      if (Optional.ofNullable(element.getDefaultValue())
          .isPresent()) {
        return element.getDefaultValue();
      } else if (element.getMandatory()) {
        throw new MappingException("error while converting");
      }
    }
    return null;
  }

  private String parseInput(Map<String, Object> mapcrosscore, String input, Element element,
      Map<String, String> mapChange) {
    String temp = JsonPath.parse(mapcrosscore).read(input);
    if (Optional.ofNullable(temp).isPresent()) {
      return Optional.ofNullable(element.getFlag())
          .map(StringUtils::trimToNull)
          .map(s1 -> checkTrueOrFalse(temp, mapChange))
          .orElse(temp);
    } else {
      if (Optional.ofNullable(element.getDefaultValue())
          .isPresent()) {
        return element.getDefaultValue();
      } else if (element.getMandatory()) {
        throw new MappingException("error while converting");
      }
    }

    return null;
  }


  private String checkTrueOrFalse( String temp, Map<String, String> mapChange) {
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
