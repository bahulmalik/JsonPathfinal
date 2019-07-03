package myPackage;

import com.beust.jcommander.Parameter;
import lombok.Data;

@Data
public class Args {

  @Parameter(names = {"-filter", "-f"})
  private String filterPath;
  @Parameter(names = {"-crosscore", "-c"})
  private String crosscorePath;
}
