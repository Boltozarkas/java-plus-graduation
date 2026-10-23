# Explore With Me (EWM)

Микросервисное приложение-афиша для публикации событий и поиска компании для участия. Дипломный проект Яндекс.Практикума.

## Содержание

- [Архитектура](#архитектура)
- [Сервисы](#сервисы)
- [Внутренний API (Feign)](#внутренний-api-feign)
- [Внешний API](#внешний-api)
- [Конфигурация](#конфигурация)
- [Запуск](#запуск)
- [Технологии](#технологии)

---

## Архитектура

Проект построен на **Spring Cloud** (Eureka, Config Server, Gateway, OpenFeign). Инфраструктурные сервисы (`discovery-server`, `config-server`, `gateway-server`) обеспечивают регистрацию, централизованную конфигурацию и единую точку входа.

Все запросы клиентов идут через **API Gateway** (`:8080`), который маршрутизирует их в нужный микросервис по имени в Eureka (`lb://`). Внутренние вызовы между сервисами реализуются через **OpenFeign** с fallback-фабриками и Resilience4j.

<details>
<summary>🗺️ Посмотреть схему взаимодействия</summary>

```text
Клиент → Gateway (:8080) → Eureka (lb://) → Микросервис
                                              │
                                              ├─→ Feign → другой микросервис (internal API)
                                              └─→ PostgreSQL (своя БД)
```
</details>

### Инфраструктурные компоненты:
* **`discovery-server`** — Eureka Server (`:8761`), регистрация и обнаружение сервисов.
* **`config-server`** — Spring Cloud Config Server (`native` профиль), централизованно раздаёт YAML-конфигурации.
* **`gateway-server`** — Spring Cloud Gateway (`:8080`), динамическая маршрутизация по Path-предикатам.

---

## Сервисы

| Сервис | Порт (Eureka) | Назначение | БД (порт) |
| :--- | :--- | :--- | :--- |
| **user-service** | случайный | Управление пользователями, ранги, баны, предупреждения | `users-db` (`:7543`) |
| **event-service** | случайный | События, категории, подборки, статистика просмотров | `events-db` (`:7544`) |
| **request-service** | случайный | Заявки на участие в событиях | `requests-db` (`:7545`) |
| **comment-service** | случайный | Комментарии к событиям (дополнительный функционал) | `comments-db` (`:7546`) |
| **stats-server** | случайный | Сбор и выдача статистики посещений | `stats-db` (`:6542`) |
| **interaction** | — | Shared-библиотека: общие DTO, исключения, Feign-клиенты, локации | — |
| **stats-client** | — | Shared-библиотека: клиент к stats-server (`DiscoveryClient` + `RetryTemplate`) | — |
| **stats-dto** | — | Shared-библиотека: базовые DTO (`EndpointHit`, `ViewStats`, `ApiError`) | — |

### Особенности проектирования
* **Изоляция данных:** Каждый микросервис владеет своей БД. Прямые Foreign Keys между сервисами отсутствуют — связь только через Feign.
* **Связи event-service:** При отображении полей `initiator` и `confirmedRequests` обращается в `user-service` и `request-service`. Настроен fallback: в случае сбоя возвращает `initiator.name: null`, `confirmedRequests: 0`.
* **Связи проверок:** `request-service` и `comment-service` при валидации сущностей обращаются к `event-service` (`EventClient`) и `user-service` (`UserClient`).
* **stats-server** — изолированный сервис. Не производит внешних вызовов, только принимает хиты и отдаёт агрегированную статистику.

---

## Внутренний API (Feign)

Внутренние эндпоинты доступны только по прямому порту конкретного сервиса (в обход Gateway). Они вынесены в модуль `interaction` и подключаются через аннотацию `@FeignClient`.

### 1. UserClient → `user-service`

| Метод | Путь | Назначение |
| :--- | :--- | :--- |
| `GET` | `/internal/users/{id}` | Получение `UserShortDto` (id, name) |
| `GET` | `/internal/users/{id}/exists` | Проверка существования пользователя |
| `GET` | `/internal/users/{id}/full` | Получение `UserFullDto` (ранг, бан, предупреждения) |
| `POST` | `/internal/users/{id}/warn` | Выдать предупреждение (автоматический бан при превышении лимита) |
| `POST` | `/internal/users/{id}/comments-count/increment` | `+1` к счётчику комментариев, запуск пересчёта ранга |
| `POST` | `/internal/users/{id}/comments-count/decrement` | `−1` к счётчику комментариев, запуск пересчёта ранга |

> **Fallback:** `UserClientFallbackFactory` — метод `findById` возвращает заглушку `UserShortDto(id, null)`, остальные методы пробрасывают `ServiceUnavailableException`.

### 2. EventClient → `event-service`

| Метод | Путь | Назначение |
| :--- | :--- | :--- |
| `GET` | `/internal/events/{id}` | Получение `EventFullDto` (для проверок в request/comment) |
| `GET` | `/internal/events/{id}/published` | Получение `EventShortDto` только со статусом `PUBLISHED` |
| `GET` | `/internal/events/{id}/exists` | Проверка существования события |

> **Fallback:** `EventClientFallbackFactory` — сразу бросает `ServiceUnavailableException` (операции критичны для бизнес-логики).

### 3. RequestClient → `request-service` (некритичные операции)

| Метод | Путь | Назначение |
| :--- | :--- | :--- |
| `GET` | `/internal/requests/events/{eventId}/confirmed-count` | Количество одобренных (`CONFIRMED`) заявок |
| `GET` | `/internal/requests/events/counts?eventIds=...` | Батч-запрос счётчиков для списков событий |
| `GET` | `/internal/requests/events/{eventId}` | Список всех заявок на конкретное событие |

> **Fallback:** `RequestClientFallbackFactory` — возвращает безопасные дефолтные значения (`0L`, пустые списки `[]`).

### 4. RequestStatusClient → `request-service` (критичные операции)

| Метод | Путь | Назначение |
| :--- | :--- | :--- |
| `POST` | `/internal/requests/events/{eventId}/status` | Массовое подтверждение или отклонение заявок |

> **Fallback:** `RequestStatusClientFallbackFactory` — если `cause` является `FeignException.Conflict`, ошибка пробрасывается дальше (возвращается `409`), иначе генерируется `ServiceUnavailableException` (503). Это позволяет `EventServiceErrorHandler` корректно обрабатывать статусы бизнес-валидации.

---

## Внешний API

Внешний API спроектирован по канонам REST и доступен через Gateway по адресу `http://localhost:8080`. Маршруты разделены на три зоны ответственности:
* 🌐 **Public** (`/events`, `/categories`, `/compilations`, `/comments`) — доступны гостям без авторизации.
* 🔐 **Private** (`/users/{userId}/events`, `/users/{userId}/requests`, `/users/{userId}/comments`) — доступны только авторизованным пользователям для работы со своими данными.
* 🛠️ **Admin** (`/admin/users`, `/admin/categories`, `/admin/events`, `/admin/compilations`, `/admin/comments`) — защищённые эндпоинты для администраторов платформы.

### Спецификация API
Спецификации в формате OpenAPI (Swagger) находятся в корневом каталоге проекта:
* Основной сервис: `ewm-main-service-spec.json`
* Сервис статистики: `ewm-stats-service-spec.json`

---

## Конфигурация

Все настройки распределённых сервисов хранятся централизованно в Config Server:

```text
infra/config-server/src/main/resources/config/
├── user-service.yaml
├── event-service.yaml
├── request-service.yaml
├── comment-service.yaml
├── stats-server.yaml
└── gateway-server.yaml
```

Каждый конфигурационный файл определяет:
* `server.port: 0` (динамический порт для обеспечения масштабируемости, кроме `gateway-server: 8080`).
* `spring.datasource.*` (индивидуальные URL, логин и пароль к своей изолированной БД).
* `eureka.client.service-url.defaultZone` (адрес для связи с Eureka).
* `spring.cloud.openfeign.circuitbreaker.enabled: true` (активация предохранителей).
* `resilience4j.circuitbreaker.configs.default.ignore-exceptions` (исключения семейства `4xx` не учитываются как ошибки сбоя сети/сервиса).

При старте микросервисы подтягивают конфигурацию через механизм `spring.config.import: "configserver:"`, предварительно обнаружив `config-server` в Eureka (`discovery.enabled: true`).

### Конфигурация окружения
* `discovery-server` использует локальный `application.yaml` с флагами `register-with-eureka: false` и `fetch-registry: false`.
* `config-server` настроен на `native` профиль: `spring.profiles.active: native` и считывает файлы из `classpath:/config`.
* `gateway-server` получает свои маршруты (routes) динамически из файла `gateway-server.yaml` в Config Server.

> 📦 **Базы данных:** `docker-compose.yml` в корне проекта предназначен **только** для развёртывания СУБД: `users-db` (`:7543`), `events-db` (`:7544`), `requests-db` (`:7545`), `comments-db` (`:7546`), `stats-db` (`:6542`). Сами Java-приложения запускаются через IDE или `java -jar`.

---

## Запуск

### Шаг 1. Поднять контейнеры с базами данных
```bash
docker compose up -d
```

### Шаг 2. Последовательно запустить сервисы
Запуск строго в указанном порядке. Перед запуском следующего сервиса убедитесь, что предыдущий успешно инициализировался.
1. `discovery-server` (`:8761`)
2. `config-server` (получит порт `0`, дождитесь его появления в панели управления Eureka)
3. `stats-server` (порт `0`)
4. `user-service` (порт `0`)
5. `request-service` (порт `0`)
6. `event-service` (порт `0`)
7. `comment-service` (порт `0`)
8. `gateway-server` (`:8080`)

### Шаг 3. Проверить работоспособность
* **Eureka Dashboard:** Откройте в браузере `http://localhost:8761` — все 7 сервисов (все, кроме discovery-server) должны иметь статус `UP`.
* **Тест шлюза:** Выполните тестовый запрос `GET http://localhost:8080/categories` — шлюз должен вернуть статус `200 OK`.

### Шаг 4. Запуск Postman-тестов
В проекте предусмотрены коллекции тестов для проверки бизнес-логики:
* `Test Explore With Me - Main service` (запросы шлюзу через `:8080`)
* `feature - comments` (проверка комментариев через `:8080`)
* `Explore with me API статистика` (запросы напрямую к статистике)

⚠️ Важное замечание по тестам статистики: Коллекция Postman для статистики жестко завязана на baseUrl = http://localhost:9090 в pre-request скриптах. Чтобы успешно пройти эти тесты, временно измените значение server.port на 9090 в файле stats-server.yaml внутри Config Server и перезапустите сервис статистики. В промышленном режиме он должен работать на порту 0.

## Технологии

* Стек: Java 21, Spring Boot 3.3.0, Spring Cloud 2023.0.2
* Инфраструктура: Spring Cloud Gateway, Eureka Service Discovery, Spring Cloud Config Server, OpenFeign
* Отказоустойчивость: Resilience4j (Circuit Breaker)
* Данные: PostgreSQL 16, Hibernate / Spring Data JPA, QueryDSL
* Сборка и контейнеризация: Maven (многомодульная структура), Docker Compose, Lombok
