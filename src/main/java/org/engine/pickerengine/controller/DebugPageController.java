package org.engine.pickerengine.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DebugPageController {

    @GetMapping({"/debug", "/debug/"})
    public String debug() {
        return "forward:/debug.html";
    }
}
