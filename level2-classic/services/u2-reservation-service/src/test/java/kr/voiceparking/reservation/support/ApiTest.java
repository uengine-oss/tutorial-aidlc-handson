package kr.voiceparking.reservation.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 웹 계층 시험의 공통 설정. 같은 설정을 쓰는 시험 클래스끼리 애플리케이션 컨텍스트를 공유한다.
 * 닫지 않는 MockMvc 구독이 상한을 채우지 않도록 구독 상한은 넉넉하게 둔다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(properties = "reservation.push.max-subscriptions=500")
@AutoConfigureMockMvc
@Import(FakeSearchConfig.class)
public @interface ApiTest {
}
