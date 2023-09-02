package jpabook.jpashop.api;


import jpabook.jpashop.domain.*;
import jpabook.jpashop.repository.OrderRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;

    /**
     * 주문 조회 V1 : 잘못된 케이스 - 엔티티 직접 노출
     * - Hibernate5Module 모듈 등록, LAZY=null 처리
     * - 엔티티가 변하면 API 스펙이 변함
     * - 트랜잭션 안에서 지연 로딩 필요
     * - 양방향 연관관계는 문제 발생 -> @JsonIgnore
     * @return List<Order>
     */
    @GetMapping("/api/v1/orders")
    public List<Order> orderV1() {
        List<Order> all = orderRepository.findAllByCriteria(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();  // LAZY 강제 초기화
            order.getDelivery().getAddress();  // LAZY 강제 초기화
            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.forEach(o -> o.getItem().getName());  // LAZY 강제 초기화
        }
        return all;
    }

    /**
     * 주문 조회 V2 :  엔티티를 조회해서 DTO로 변환(fetch join 사용X)
     * - 트랜잭션 안에서 지연 로딩 필요 (너무 많은 SQL 실행)
     * - order 1번 + member, address N번(order 조회수) + orderItem N번(order 조회수) + item N번 (orderItem 조회수)
     * @return List<OrderDto>
     */
    @GetMapping("/api/v2/orders")
    public List<OrderDto> orderV2() {
        List<Order> orders = orderRepository.findAllByCriteria(new OrderSearch());

        return orders.stream()
                .map(OrderDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 주문 조회 V3 :  엔티티를 조회해서 DTO로 변환(fetch join 최적화)
     * - fetch join 시 distinct 사용하여 중복 제거 필요
     * - 페이징 불가능
     * @return List<OrderDto>
     */
    @GetMapping("/api/v3/orders")
    public List<OrderDto> orderV3() {
        List<Order> orders = orderRepository.findAllWithItem();

        return orders.stream()
                .map(OrderDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 주문 조회 V3.1 :  fetch join 최적화 + paging
     * - ToOne 관계만 우선 모두 페치 조인으로 최적화
     * - 컬렉션 관계는 hibernate.default_batch_fetch_size, @BatchSize로 최적화
     * @return List<OrderDto>
     */
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> orderV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);

        return orders.stream()
                .map(OrderDto::new)
                .collect(Collectors.toList());
    }

    @Getter
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            orderItems = order.getOrderItems().stream()
                    .map(OrderItemDto::new)
                    .collect(Collectors.toList());
        }
    }

    @Getter
    static class OrderItemDto {
        private String itemName;    // 상품명
        private int orderPrice;     // 주문 가격
        private int count;          // 주문 수량

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }
}