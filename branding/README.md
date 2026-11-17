# Zephyr-Mobile

Identidade de produto escolhida em 15/11/2026: Z prateado dentro de quatro segmentos verdes.
Inspirada na linguagem visual da geração Xbox 360; não é o logotipo oficial do Xbox.
A identidade corporativa da Pixel Code permanece inalterada.

`generate.py` é a fonte única da geometria. Exporta SVG, VectorDrawable, monochrome Android 13,
splash Android 12+, ícones raster de compatibilidade, favicon, Open Graph e capa do repositório.

- Verde-base: `#86D92F`, com luz lima; prata com gradiente metálico; fundo esmaltado verde profundo. A versão monocromática mantém preenchimento sólido.
- Adaptive icon: emblema inteiro dentro do círculo seguro central de 66 dp em viewport 108 dp.
- Uso no app: `zephyr_brand_mark` sem margens de launcher. Arquivos/atalhos: `zephyr_app_icon`.
- Vetores: `python branding/generate.py --vectors-only` (stdlib).
- Todos os exports: `python branding/generate.py` (Pillow, librsvg e gdk-pixbuf do sistema).
- Brilho estático em camadas vetoriais, sem animação contínua, filtro de blur ou trabalho no loop da emulação.

Os arquivos com nomes antigos usados por referências de compatibilidade agora contêm a marca
Zephyr-Mobile; nomes técnicos não alteram assinatura, pacote instalado ou dados do usuário.
