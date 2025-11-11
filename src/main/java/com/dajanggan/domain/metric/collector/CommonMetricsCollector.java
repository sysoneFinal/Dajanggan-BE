//package com.dajanggan.domain.metric.collector;
//
//import com.dajanggan.domain.instance.repository.InstanceRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.time.OffsetDateTime;
//
//@Service
//@RequiredArgsConstructor
//public class CommonMetricsCollector {
//
//    private final InstanceRepository instanceRepository;
//    private final SessionMetricsCollector sessionMetricsCollector;
//
//
//    /** 1분마다 전체 인스턴스 메트릭 수집 */
//    @Scheduled(fixedRate = 60000)
//    public void collectAllInstances() {
//        OffsetDateTime collectedAt = OffsetDateTime.now();
//
//        // (1) 등록된 인스턴스 목록 조회
//        List<DatabaseInstance> instances = instanceRepository.findAllEnabled();
//        if (instances.isEmpty()) {
//            System.out.println("수집 대상 인스턴스가 없습니다.");
//            return;
//        }
//
//        // (2) 인스턴스별 수집 실행
//        for (DatabaseInstance instance : instances) {
//            try {
//                System.out.printf("[%s] Collecting metrics from %s (%s:%d)%n",
//                        collectedAt, instance.getName(), instance.getHost(), instance.getPort());
//
//                // (3) 하위 Collector 호출
//                sessionMetricsCollector.collect(instance, collectedAt);
//
//
//            } catch (Exception e) {
//                System.err.printf("[%s] %s 수집 실패: %s%n",
//                        collectedAt, instance.getName(), e.getMessage());
//            }
//        }
//    }
//}
