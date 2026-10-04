/* ================================================================
 * 精选画廊 · 数据文件（由你自己维护）
 * ================================================================
 * 社交广场「精选画廊 3D 轮播」的图片与文案都来自这里。
 *
 * 维护方法（换图只需两步）：
 *   1) 图片：把新图放进 public/mkp/gallery/ 下（如 01.jpg … 08.jpg），
 *      或直接使用项目内已有的图（当前用的就是 public/mkp/wall/，
 *      与桌面端胶片画廊同一批真实素材，不依赖外网）；
 *   2) 文字：改下面数组即可 ——
 *        img  图片地址（项目内推荐 /mkp/gallery/01.jpg 这种本地路径）
 *        t1 / t2  卡片上的大标题（t2 可为 ''）
 *        sub  卡片小字说明（可为 ''）
 *   顺序即轮播顺序，条目数量随意（当前 8 张）。
 * ================================================================ */
export default [
  { img: '/mkp/wall/w1-beagle.jpg',    t1: 'BEAGLE',    t2: 'DAY',    sub: '比格犬的上午十点' },
  { img: '/mkp/wall/w2-retriever.jpg', t1: 'RETRIEVER', t2: 'NINE',   sub: '金毛与它的九号球' },
  { img: '/mkp/wall/w3-superdog.jpg',  t1: 'SUPER',     t2: 'DOG',    sub: '披风不需要理由' },
  { img: '/mkp/wall/w4-husky.jpg',     t1: 'HUSKY',     t2: 'WINTER', sub: '雪国来的拆家高手' },
  { img: '/mkp/wall/w5-bunny.jpg',     t1: 'BUNNY',     t2: 'FIVE',   sub: '兔子的第五次探头' },
  { img: '/mkp/wall/w6.jpg',           t1: 'PORTRAIT',  t2: 'SIX',    sub: '棚拍肖像 N° 06' },
  { img: '/mkp/wall/w7-tabby.jpg',     t1: 'TABBY',     t2: 'NOON',   sub: '狸花猫的午睡哲学' },
  { img: '/mkp/wall/w8-labrador.jpg',  t1: 'LABRADOR',  t2: 'SUNSET', sub: '拉布拉多的海边黄昏' }
]
