/**
 * RAGent 웹뷰 소스 분석 및 테마 제어를 위한 스크립트
 * Notion, GitHub 등 특정 사이트의 블록/라인 정보를 추출합니다.
 */
(function () {
  /**
   * 주어진 엘리먼트로부터 Notion의 블록 ID 또는 특정 형식의 UUID를 가진 부모 블록을 찾습니다.
   * @param {HTMLElement} element - 검색을 시작할 엘리먼트
   * @returns {HTMLElement|null} - 찾은 블록 엘리먼트 또는 null
   */
  function findBlock(element) {
    let current = element;
    while (current && current !== document.body) {
      if (current.getAttribute) {
        const blockId = current.getAttribute('data-block-id');
        const id = current.id;
        // data-block-id 속성이 있거나, ID가 UUID 형식인 경우 블록으로 간주
        if (blockId || (id && /^[0-9a-f]{8}-[0-9a-f-]{27,}$/i.test(id))) {
          return current;
        }
      }
      current = current.parentElement;
    }
    return null;
  }

  /**
   * 화면의 특정 영역(rect)에 해당하는 콘텐츠를 분석하여 소스 정보를 반환합니다.
   * @param {Object} config - 설정 객체 (rect: {left, top, right, bottom}, sourceType: 'notion'|'github' 등)
   * @returns {string} - 분석 결과 JSON 문자열
   */
  window.ragentResolveSelection = function (config) {
    const dpr = window.devicePixelRatio || 1;
    const input = config.rect;

    // 장치 픽셀 비율(DPR)을 고려한 실제 좌표 계산
    const r = {
      left: input.left / dpr,
      top: input.top / dpr,
      right: input.right / dpr,
      bottom: input.bottom / dpr
    };

    const cx = (r.left + r.right) / 2;
    const cy = (r.top + r.bottom) / 2;

    // 검사할 포인트들을 정의 (중앙, 사각형 모서리, 각 변의 중앙 등 9개 지점)
    const points = [
      [cx, cy],
      [r.left, r.top], [r.right, r.top],
      [r.left, r.bottom], [r.right, r.bottom],
      [cx, r.top], [cx, r.bottom], [r.left, cy], [r.right, cy]
    ].map(function (point) {
      // 뷰포트 범위를 벗어나지 않도록 클램핑
      return [
        Math.max(0, Math.min(window.innerWidth - 1, point[0])),
        Math.max(0, Math.min(window.innerHeight - 1, point[1]))
      ];
    });

    // 중앙 지점의 엘리먼트 확인
    const point = document.elementFromPoint(cx, cy) ||
      document.elementFromPoint(points[0][0], points[0][1]);

    // 모든 지점에서 해당 좌표에 있는 엘리먼트들을 수집
    const pointElements = points.reduce(function (result, p) {
      return result.concat(document.elementsFromPoint(p[0], p[1]));
    }, []);

    // 수집된 엘리먼트들 중에서 가장 먼저 발견되는 Notion 블록 검색
    const pointBlock = pointElements.map(findBlock).find(Boolean);

    /**
     * 화면에 보이고 선택 영역(r)과 겹치는 엘리먼트들을 재귀적으로 탐색합니다.
     */
    const intersecting = Array.from(document.body.children).reduce(function traverse(result, element) {
      const b = element.getBoundingClientRect();

      // 가지치기(Pruning): 화면에 보이고 선택 영역(r)과 겹치는 경우만 내부 로직 실행
      if (b.width > 0 && b.height > 0 &&
          b.left < r.right && b.right > r.left &&
          b.top < r.bottom && b.bottom > r.top) {
        
        result.push(element);

        // 겹치는 부모의 자식 노드들만 재귀적으로 탐색
        Array.from(element.children).reduce(traverse, result);
      }
      return result;
    }, []);

    // 후보 블록 리스트 생성 (좌표 기반 엘리먼트 + 겹치는 엘리먼트)
    const blockCandidates = Array.from(new Set(
      pointElements.concat(intersecting)
        .map(findBlock)
        .filter(Boolean)
    ));

    /**
     * 엘리먼트와 선택 영역(r)이 겹치는 면적을 계산합니다.
     */
    const calculateOverlapArea = function (element) {
      const b = element.getBoundingClientRect();
      const horizontalOverlap = Math.max(0, Math.min(b.right, r.right) - Math.max(b.left, r.left));
      const verticalOverlap = Math.max(0, Math.min(b.bottom, r.bottom) - Math.max(b.top, r.top));
      return horizontalOverlap * verticalOverlap;
    };

    // 자식 블록을 포함하지 않는 최하위 리프(Leaf) 블록들만 필터링
    const leafBlocks = blockCandidates.filter(function (element) {
      return !blockCandidates.some(function (other) {
        return other !== element && element.contains(other);
      });
    });

    // 겹치는 면적이 가장 큰 블록을 결정
    const resolvedBlock = (leafBlocks.length ? leafBlocks.slice() : blockCandidates.slice())
      .sort(function (a, b) {
        return calculateOverlapArea(b) - calculateOverlapArea(a);
      })[0] || pointBlock;

    // Notion용 블록 정렬 (위에서 아래, 왼쪽에서 오른쪽 순서)
    const notionBlocks = (leafBlocks.length
      ? leafBlocks.slice()
      : (pointBlock ? [pointBlock] : [])
    ).sort(function (a, b) {
      const aRect = a.getBoundingClientRect();
      const bRect = b.getBoundingClientRect();
      return aRect.top === bRect.top ? aRect.left - bRect.left : aRect.top - bRect.top;
    });

    // 자식 요소를 포함하지 않는 가장 구체적인 엘리먼트들 추출
    const selected = intersecting.filter(function (element) {
      return !intersecting.some(function (other) {
        return other !== element && element.contains(other);
      });
    });

    // 텍스트를 추출할 노드 결정
    const textNodes = config.sourceType === 'notion' && notionBlocks.length
      ? notionBlocks
      : (resolvedBlock ? [resolvedBlock] : (selected.length ? selected : [point]));

    // 텍스트 추출 및 정제 (최대 12,000자)
    const text = Array.from(new Set(textNodes.map(function (element) {
      return element && (element.innerText || element.textContent) || '';
    }).filter(Boolean))).join(' ').replace(/\s+/g, ' ').trim().slice(0, 12000);

    // 상위 헤더(H1-H6) 경로 탐색
    const headings = [];
    let parent = resolvedBlock || point;
    while (parent) {
      if (/^H[1-6]$/.test(parent.tagName) && (parent.innerText || '').trim()) {
        headings.unshift(parent.innerText.trim());
      }
      parent = parent.parentElement;
    }

    // 결과 객체 초기화
    const result = {
      selectedText: text || null,
      sourceType: config.sourceType,
      sourceUrl: location.href,
      canonicalUrl: null,
      canonicalUrls: [],
      pageId: null,
      blockId: resolvedBlock ? (resolvedBlock.getAttribute('data-block-id') || resolvedBlock.id || null) : null,
      blockIds: [],
      filePath: null,
      startLine: null,
      endLine: null,
      headingPath: headings
    };

    // 소스 타입별 상세 처리 (Notion vs Others/GitHub)
    if (result.sourceType === 'notion') {
      // Notion 특정 처리
      result.pageId = location.pathname.split('/').filter(Boolean).pop() || null;
      result.blockIds = Array.from(new Set(notionBlocks.map(function (block) {
        return block.getAttribute('data-block-id') || block.id || null;
      }).filter(Boolean)));
      result.canonicalUrls = result.blockIds.map(function (blockId) {
        return location.href.split('#')[0] + '#' + blockId;
      });
      result.blockId = result.blockIds[0] || null;
      result.canonicalUrl = result.canonicalUrls[0] || null;
    } else {
      // GitHub 또는 일반 웹사이트 처리
      const pathMatch = location.pathname.match(/\/blob\/[^/]+\/(.+)$/);
      const selectedLink = selected.concat(intersecting).map(function (e) {
        return e.closest && e.closest('a[href*="/blob/"]');
      }).find(Boolean);
      const linkedPathMatch = selectedLink && selectedLink.href.match(/\/blob\/[^/]+\/(.+?)(?:[?#]|$)/);

      result.filePath = pathMatch ? pathMatch[1] : (linkedPathMatch ? linkedPathMatch[1] : null);

      // 라인 번호 엘리먼트 수집
      const lineElements = Array.from(document.querySelectorAll('[data-line-number], [id^="LC"], [id^="L"]'))
        .filter(function (e) {
          const b = e.getBoundingClientRect();
          return b.width > 0 && b.height > 0 &&
            b.left < r.right && b.right > r.left &&
            b.top < r.bottom && b.bottom > r.top;
        });

      result.debugLineCount = lineElements.length;
      result.debugLineIds = lineElements.slice(0, 8).map(function (e) {
        return e.getAttribute('data-line-number') || e.id || '';
      });

      // 라인 번호 추출 및 정렬
      const lines = Array.from(new Set(lineElements.map(function (e) {
        return e.getAttribute('data-line-number') || e.id || '';
      }).map(function (value) {
        const match = String(value).match(/(?:LC|L|line-)?(\d+)/i);
        return match ? Number(match[1]) : null;
      }).filter(Boolean))).sort(function (a, b) { return a - b; });

      if (lines.length) {
        // 코드 라인 정보가 있는 경우
        result.startLine = lines[0];
        result.endLine = lines[lines.length - 1];
        result.canonicalUrl = location.href.split('#')[0] + '#L' + result.startLine +
          (result.endLine > result.startLine ? '-L' + result.endLine : '');
      } else {
        // 마크다운 문서 등 일반 텍스트 영역인 경우
        const markdown = (point && point.closest && point.closest('article.markdown-body, .markdown-body')) ||
          document.querySelector('article.markdown-body, .markdown-body');

        if (markdown) {
          const markdownHeadings = Array.from(markdown.querySelectorAll('h1, h2, h3, h4, h5, h6'));
          const heading = markdownHeadings.filter(function (h) {
            return h.getBoundingClientRect().top <= cy;
          }).sort(function (a, b) {
            return b.getBoundingClientRect().top - a.getBoundingClientRect().top;
          })[0];

          const headingText = heading && (heading.innerText || heading.textContent || '').trim();
          const slug = headingText
            ? headingText.toLowerCase()
                .replace(/[^a-z0-9\uAC00-\uD7A3\s-]/g, '')
                .trim()
                .replace(/\s+/g, '-')
            : null;

          const anchor = heading && (heading.id || slug);
          const readmeLink = Array.from(document.querySelectorAll('a[href*="/blob/"]'))
            .map(function (a) { return a.href; })
            .find(function (href) {
              return /\/blob\/[^/]+\/README(?:\.md)?(?:[?#]|$)/i.test(href);
            });

          const readmeBase = readmeLink
            ? readmeLink.split('#')[0]
            : (result.filePath
                ? location.origin + location.pathname.replace(/\/[^/]+$/, '/' + result.filePath)
                : location.href.split('#')[0]);

          result.filePath = result.filePath || 'README.md';
          result.canonicalUrl = anchor ? readmeBase + '#' + anchor : readmeBase;
          result.headingPath = headingText ? [headingText] : [];
        }
      }
    }
    return JSON.stringify(result);
  };

  /**
   * Notion 앱의 스크롤 문제를 해결하고 테마에 따른 배경색을 주입합니다.
   * @param {boolean} darkTheme - 다크 모드 여부
   */
  window.ragentInjectNotionScrollFix = function (darkTheme) {
    let style = document.getElementById('ragent-notion-scroll-fix');
    if (!style) {
      style = document.createElement('style');
      style.id = 'ragent-notion-scroll-fix';
      document.head.appendChild(style);
    }
    // 스크롤 영역 강제 활성화
    const baseRules =
      'html, body, #notion-app, #notion-app > div, .notion-frame, .notion-scroller {' +
      ' overflow-y: auto !important; height: auto !important; }';

    // 테마별 배경색 및 테두리 색상 설정
    const colorRules = darkTheme
      ? 'html, body, #notion-app, #notion-app > div {' +
          ' background: #191919 !important; color: #e6e6e6 !important; }' +
          ' #notion-app * { border-color: #3a3a3a !important; }'
      : 'html, body, #notion-app, #notion-app > div {' +
          ' background: #ffffff !important; color: #191919 !important; }';

    style.textContent = baseRules +
      ' :root { color-scheme: ' + (darkTheme ? 'dark' : 'light') + '; } ' +
      colorRules;
  };

  /**
   * 일반 웹페이지에 다크 테마 스타일을 주입합니다.
   * @param {boolean} darkTheme - 다크 모드 여부
   */
  window.ragentInjectDarkTheme = function (darkTheme) {
    let style = document.getElementById('ragent-dark-theme');
    if (!style) {
      style = document.createElement('style');
      style.id = 'ragent-dark-theme';
      document.head.appendChild(style);
    }
    style.textContent = darkTheme
      ? 'html, body { background: #0d1117 !important; color: #e6edf3 !important; }' +
          ' body * { border-color: #30363d !important; }' +
          ' a { color: #58a6ff !important; }'
      : 'html, body { background: #ffffff !important; color: #24292f !important; }';
  };
})();
