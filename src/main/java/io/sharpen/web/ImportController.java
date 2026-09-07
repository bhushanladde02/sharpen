package io.sharpen.web;

import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.Person;
import io.sharpen.service.ImportService;
import io.sharpen.service.ImportService.ImportResult;
import io.sharpen.service.PersonService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/import")
public class ImportController {

    private final PersonService people;
    private final ImportService imports;

    public ImportController(PersonService people, ImportService imports) {
        this.people = people;
        this.imports = imports;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("contexts", UsageContext.values());
        return "import";
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(defaultValue = "PROFESSIONAL") UsageContext context,
                         Model model) {
        Person me = people.requireCurrent();
        model.addAttribute("contexts", UsageContext.values());
        ImportResult result;
        if (file == null || file.isEmpty()) {
            result = new ImportResult(0, 0, 0, List.of("Choose a CSV or JSON file first."));
        } else {
            try {
                String body = new String(file.getBytes(), StandardCharsets.UTF_8);
                String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
                result = name.endsWith(".json") || body.stripLeading().startsWith("{")
                        ? imports.importExtensionJson(me, body, context)
                        : imports.importCsv(me, body, context);
            } catch (IOException e) {
                result = new ImportResult(0, 0, 0, List.of("Could not read the file: " + e.getMessage()));
            }
        }
        model.addAttribute("result", result);
        return "import";
    }
}
