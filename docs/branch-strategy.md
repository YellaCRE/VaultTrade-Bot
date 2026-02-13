# Git 운영 표준 (Solo Developer Optimized)

---

# 1. 운영 원칙

* main은 항상 배포 가능 상태 유지
* 기능 단위로 짧은 브랜치 운영
* PR은 “검토 목적”이 아니라 “기록 목적”
* 커밋 메시지는 릴리즈 추적용

---

# 2. 브랜치 전략

## 사용 모델

**Lightweight Trunk-Based Development**

> main + short-lived feature 브랜치만 사용

---

## 브랜치 구조

| 브랜치        | 설명        |
| ---------- | --------- |
| main       | 운영/배포 브랜치 |
| feature/*  | 기능 개발     |
| fix/*      | 버그 수정     |
| refactor/* | 리팩토링      |

❌ develop 브랜치 사용 안 함
❌ release 브랜치 사용 안 함

---

## 브랜치 네이밍 규칙

```
type/short-description
```

예시:

```
feature/user-signup
fix/login-null-error
refactor/order-aggregate
```

이슈 번호는 1인 개발이면 생략 가능

---

# 3. 작업 흐름 (Workflow)

### 1️⃣ main 최신화

```
git checkout main
git pull origin main
```

### 2️⃣ 브랜치 생성

```
git checkout -b feature/user-signup
```

### 3️⃣ 개발 + 테스트

* 단위 테스트 작성 (TDD 권장)
* 로컬 빌드 확인

### 4️⃣ main 병합

```
git checkout main
git merge --squash feature/user-signup
git commit -m "feat(user): add signup logic"
git push origin main
```

### 5️⃣ 브랜치 삭제

```
git branch -d feature/user-signup
```

---

# 4. 커밋 메시지 규칙 (필수)

형식:

```
type(scope): subject
```

예:

```
feat(auth): add JWT validation
fix(order): resolve null pointer
refactor(domain): separate aggregate logic
test(user): add signup service test
```

---

## 타입 목록

| 타입       | 용도    |
| -------- | ----- |
| feat     | 기능 추가 |
| fix      | 버그    |
| refactor | 구조 개선 |
| test     | 테스트   |
| chore    | 설정 변경 |
| docs     | 문서    |
