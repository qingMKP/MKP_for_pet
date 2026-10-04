package com.mkp.petservice.controller;

import com.mkp.common.dto.PetServiceAskDTO;
import com.mkp.common.result.R;
import com.mkp.petservice.service.PetAdviceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pet-service")
public class PetServiceController {

    private final PetAdviceService petAdviceService;

    public PetServiceController(PetAdviceService petAdviceService) {
        this.petAdviceService = petAdviceService;
    }

    @PostMapping("/advice")
    public R<String> advice(@RequestBody PetServiceAskDTO dto) {
        try {
            return R.ok(petAdviceService.generateAdvice(dto));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }
}
