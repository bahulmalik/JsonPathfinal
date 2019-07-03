package myPackage;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class POJO {

  Map<String, String> changeMap;
  Map<String, String> filterMap;
  List<Element> elementList;
}
