package myPackage;

import java.util.List;
import lombok.Data;

@Data
public class Element {

  List<String> concat;
  String input;
  String mapping;
  String flag;
  String constant;
  Boolean mandatory;
  String defaultValue;
}
