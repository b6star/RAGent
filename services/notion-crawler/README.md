# RAGent Notion Crawler

공개 Notion 페이지를 실제 Chromium으로 렌더링하는 private Cloud Run
서비스다. Cloud Functions의 `syncPublicSources` task worker만 이 서비스를
호출한다.

구현은 [Playwright로 공개 Notion 페이지 수집하기](https://app.notion.com/p/3c3974b809de81bb8997ea3e6bceb703)의 검증 결과를 반영한다.

- 토글·댓글 답글을 가능한 범위에서 펼친다.
- 페이지를 구간별로 스크롤하며 지연 렌더링된 텍스트와 링크를 수집한다.
- 여러 snapshot의 반복 텍스트 블록을 제거해 병합한다.
- URL 표현 대신 32자리 Notion Page ID로 방문·Queue 중복을 제거한다.
- 공개 Notion 하위 링크만 제한된 깊이와 페이지 수 안에서 방문한다.
- private/local 네트워크로 향하는 브라우저 요청을 차단한다.
- 결과 JSON은 gzip으로 압축해 Firebase Storage에 immutable snapshot으로 저장한다.

## 로컬 검증

```powershell
npm install
npx playwright install chromium
npm test
```

## Cloud Run 배포

다음 명령은 이 디렉터리에서 실행한다.

```powershell
gcloud run deploy ragent-notion-crawler `
  --source . `
  --project ragent-d6b01 `
  --region asia-northeast3 `
  --no-allow-unauthenticated `
  --memory 4Gi `
  --cpu 4 `
  --timeout 1200 `
  --max-instances 2
```

배포 후 Cloud Functions Gen 2 실행 서비스 계정에 다음 최소 권한을 부여한다.

- Cloud Run service의 `roles/run.invoker`
- 프로젝트 Cloud Tasks의 `roles/cloudtasks.enqueuer`
- Notion crawler 서비스 계정의 snapshot bucket `roles/storage.objectCreator`

마지막으로 Functions 배포 시 `NOTION_CRAWLER_URL` parameter에 private Cloud
Run URL을 입력한다. 서비스는 공개 호출을 허용하지 않는다.
