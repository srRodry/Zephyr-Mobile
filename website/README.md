# Zephyr-Mobile · Anel

Site público do projeto Pixel Code, publicado pelo workflow `Site` na GitHub Pages.
A página oficial é `anel/index.html`; o build não depende de npm nem de dados do núcleo.

```sh
python website/anel/build.py
python -m http.server 4173 --directory website/dist
```

Inclui português/inglês, download com fallback para Releases, favicon, ícones e Open Graph.
O JavaScript consulta exclusivamente os lançamentos Research deste repositório.
Não publica números de FPS ou resultados de outro dispositivo.

Os recursos de marca são gerados por `python branding/generate.py` a partir da mesma
geometria que gera os vetores Android e a capa do repositório. A marca Pixel Code permanece
inalterada. As fontes anteriores do site e simulador estão preservadas para referência;
não são publicadas como se representassem a interface atual.
