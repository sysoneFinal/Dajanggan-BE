package com.dajanggan.domain.engine.hottable.controller;

import com.dajanggan.domain.engine.hottable.dto.HotTableDto;
import com.dajanggan.domain.engine.hottable.service.HotTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard/hotTable")
public class HotTableController {
        private final HotTableService hotTableService;
        /**
         * /dashboard/hot-table?databaseId=1
         * /dashboard/hot-table?instanceId=10 둘 다 받을 수 있게 해둠
         */
        @GetMapping
        public ResponseEntity<HotTableDto.DashboardResponse> getHotTableDashboard(
                @RequestParam(name = "databaseId", required = false) Long databaseId,
                @RequestParam(name = "instanceId", required = false) Long instanceId
        ) {
            // 1) databaseId가 최우선
            Long resolvedDatabaseId = databaseId;

            // 2) 안 들어왔으면 instanceId로부터 매핑
            if (resolvedDatabaseId == null && instanceId != null) {
                resolvedDatabaseId = hotTableService.resolveDatabaseId(instanceId);
            }

            // 3) 그래도 없으면 디폴트
            if (resolvedDatabaseId == null) {
                resolvedDatabaseId = 1L;
            }

            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusHours(24);

            HotTableDto.DashboardResponse resp =
                    hotTableService.getDashboard(resolvedDatabaseId, start, end);

            return ResponseEntity.ok(resp);
        }

}
