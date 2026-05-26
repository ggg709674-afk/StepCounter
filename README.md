# 우주만보기

개인용 안드로이드 만보기 앱. Step Counter 센서를 이용해 일일 걸음수 측정.

## APK 빌드 (GitHub Actions)

1. 이 폴더를 GitHub 레포로 push
2. push 자동 또는 Actions 탭에서 `Build APK` 워크플로 수동 실행
3. 완료되면 Artifacts에서 `WoozooPedometer-debug.zip` 다운로드 → 풀어서 APK를 폰에 설치

## 폰에 설치

1. 폰 설정 → 보안 → "출처를 알 수 없는 앱 설치" 허용
2. APK 파일을 폰으로 옮기고 탭하여 설치

## 기능

- 일일 걸음수 (자정 자동 리셋)
- 목표 설정 + 진행률 바
- 거리/칼로리 추정
- 로컬에만 저장 (서버 X)
