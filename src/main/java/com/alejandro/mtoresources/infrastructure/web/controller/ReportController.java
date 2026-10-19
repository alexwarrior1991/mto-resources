package com.alejandro.mtoresources.infrastructure.web.controller;

import com.alejandro.mtoresources.application.dto.report.PositionDiscrepancyReport;
import com.alejandro.mtoresources.application.service.PositionDiscrepancyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ResourcesApiPaths.REPORTS)
@RequiredArgsConstructor
@Tag(name = "Reports")
public class ReportController {

    private final PositionDiscrepancyService discrepancyService;

    @Operation(summary = "Position profile against category", description = "Reads Keycloak (who holds each mto-position-* profile, "
            + "directly) and compares it with the category of every active person with a username: a profile that is not the one of "
            + "the category, several, none, and users with a position profile and no active record here. It says, it does not fix "
            + "(a stand-in may hold another profile on purpose). 503 KCK-503 if Keycloak cannot be read.")
    @GetMapping("/position-discrepancies")
    public ResponseEntity<PositionDiscrepancyReport> positionDiscrepancies() {
        return ResponseEntity.ok(discrepancyService.report());
    }
}
