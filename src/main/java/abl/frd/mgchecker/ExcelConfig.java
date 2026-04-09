package abl.frd.mgchecker;

import org.apache.poi.util.IOUtils;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class ExcelConfig {

    @PostConstruct
    public void init() {
        // Set global POI limit to 200MB
        IOUtils.setByteArrayMaxOverride(200_000_000);
    }
}