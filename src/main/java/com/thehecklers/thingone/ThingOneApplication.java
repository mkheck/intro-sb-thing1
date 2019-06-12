package com.thehecklers.thingone;

import lombok.*;
import lombok.extern.java.Log;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.Stream;

@SpringBootApplication
public class ThingOneApplication {
    @Bean
    CommandLineRunner demoData(CoffeeRepo repo) {
        return args -> {
//            repo.deleteAll();
//
//            Stream.of("Kaldi's Coffee", "Cafe de Oro", "Cafe Cereza", "Espresso Roast")
//                    .map(Coffee::new)
//                    .forEach(repo::save);
//
//            repo.forEach(System.out::println);

            repo.deleteAll().thenMany(
                    Flux.just("Kaldi's Coffee", "Cafe de Oro", "Cafe Cereza", "Espresso Roast")
                            .map(Coffee::new)
                            .flatMap(repo::save))
                    .thenMany(repo.findAll())
                    .subscribe(System.out::println);
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(ThingOneApplication.class, args);
    }

}

@Log
//@EnableWebSecurity
//class SecurityConfig extends WebSecurityConfigurerAdapter {
@EnableWebFluxSecurity
class SecurityConfig {
    private final PasswordEncoder pwEnc = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Bean
        //UserDetailsService authentication() {
    MapReactiveUserDetailsService authentication() {
        UserDetails mark = User.builder()
                .username("mark")
                .password(pwEnc.encode("BetterPassword1!"))
                .roles("USER", "ADMIN")
                .build();

        UserDetails bob = User.builder()
                .username("bob")
                .password(pwEnc.encode("password"))
                .roles("USER")
                .build();


        log.info("      Mark's password: " + mark.getPassword());
        log.info("       Bob's password: " + bob.getPassword());

        //return new InMemoryUserDetailsManager(mark, bob);
        return new MapReactiveUserDetailsService(mark, bob);
    }

    //@Override   // Authorization
    //protected void configure(HttpSecurity http) throws Exception {
    SecurityWebFilterChain authorization(ServerHttpSecurity http) {
        http
                //.authorizeRequests().mvcMatchers("/actuator/**").hasRole("ADMIN")
                //.anyRequest().authenticated()
                .authorizeExchange().pathMatchers("/actuator/**").hasRole("ADMIN")
                .anyExchange().authenticated()
                .and()
                .formLogin()
                .and()
                .httpBasic();

        return http.build();
    }
}

@EnableBinding(Source.class)
@EnableScheduling
@RestController
@RequestMapping("/coffees")
@AllArgsConstructor
class ThingOneController {
    private final CoffeeRepo repo;
    private final Source source;
    private final CoffeeGenerator generator;

//    @GetMapping("/hello")
//    String greeting() {
//        return "Hello World!";
//    }

//    @Scheduled(fixedRate = 100)
//    void sendSomeCoffees() {
//        source.output().send(MessageBuilder.withPayload(generator.generate()).build());
//    }

    @GetMapping
    Flux<Coffee> getAllCoffees() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    Mono<Coffee> getCoffeeById(@PathVariable String id) {
        return repo.findById(id);
    }

    @GetMapping("/search")
        //Coffee searchForCoffee(@RequestParam String name) {
    Mono<Coffee> searchForCoffee(@RequestParam String name) {
/*
        Coffee searchCoffee = repo.findByName(name);

        if (null == searchCoffee) {
            searchCoffee = repo.save(new Coffee(name));
        }

        source.output().send(MessageBuilder.withPayload(searchCoffee).build());
        return searchCoffee;
*/

        return repo.findByName(name)
                .switchIfEmpty(repo.save(new Coffee(name)))
                .doOnSuccess(coffee -> source.output().send(MessageBuilder.withPayload(coffee).build()));

    }
}

@Component
class CoffeeGenerator {
    static private int i = 0;

    Coffee generate() {
        return new Coffee("Coffee" + i++);
    }
}

interface CoffeeRepo extends ReactiveCrudRepository<Coffee, String> {
    Mono<Coffee> findByName(String name);
}

@Document
@Data
@NoArgsConstructor
@RequiredArgsConstructor
class Coffee {
    @Id
    private String id;
    @NonNull
    private String name;
}