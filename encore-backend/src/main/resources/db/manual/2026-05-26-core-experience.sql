SET NAMES utf8mb4;
USE encore;

ALTER TABLE encore_show
  ADD COLUMN IF NOT EXISTS intro TEXT NULL AFTER description,
  ADD COLUMN IF NOT EXISTS cast_members TEXT NULL AFTER intro,
  ADD COLUMN IF NOT EXISTS creative_team TEXT NULL AFTER cast_members,
  ADD COLUMN IF NOT EXISTS full_synopsis TEXT NULL AFTER creative_team;

UPDATE encore_show
SET
  intro = '安德鲁·劳埃德·韦伯经典音乐剧，讲述巴黎歌剧院幽暗传说与天才魅影的爱恋执念。',
  cast_members = '克里斯汀、魅影、拉乌尔、卡洛塔及巴黎歌剧院合唱团',
  creative_team = '作曲：安德鲁·劳埃德·韦伯；原著：加斯东·勒鲁；舞台监督：ENCORE 剧院制作组',
  full_synopsis = '年轻女高音克里斯汀在巴黎歌剧院崭露头角，却被隐藏在剧院深处的神秘魅影牵引。魅影以音乐为纽带训练她、保护她，也试图占有她。当克里斯汀与旧友拉乌尔重逢，爱、才华、恐惧与自由之间的冲突逐渐推向剧院地下湖畔的终局。'
WHERE id = 's-001' AND intro IS NULL;

UPDATE encore_show
SET
  intro = '柴可夫斯基传世芭蕾，以纯白群舞、浪漫双人舞和命运悲剧呈现古典芭蕾之美。',
  cast_members = '奥杰塔/奥吉莉娅、齐格弗里德王子、罗特巴特、王后及天鹅群舞',
  creative_team = '作曲：彼得·伊里奇·柴可夫斯基；编舞：经典马林斯基版本改编；演出：ENCORE 芭蕾舞团',
  full_synopsis = '王子齐格弗里德在湖畔邂逅被魔法变成天鹅的公主奥杰塔，并许下真爱誓言。魔王罗特巴特以黑天鹅奥吉莉娅设下骗局，使王子在舞会上误认爱人。誓言破碎之后，奥杰塔与王子以爱对抗诅咒，在月色湖畔迎来悲怆而高贵的结局。'
WHERE id = 's-002' AND intro IS NULL;

UPDATE encore_show
SET
  intro = 'Coldplay 世界巡演特别场，以沉浸灯光、全场合唱和星河视觉打造大型音乐现场。',
  cast_members = 'Coldplay 乐队、巡演乐手、现场和声与舞美团队',
  creative_team = '音乐总监：Coldplay；舞美设计：Music of the Spheres 巡演团队；本地执行：ENCORE Live',
  full_synopsis = '演唱会围绕“星球、爱与连接”展开，串联多首代表作品与新专辑曲目。观众将在星河灯海、腕带互动和环绕音响中共同完成一场大型合唱，体验从温柔抒情到全场沸腾的完整音乐旅程。'
WHERE id = 's-003' AND intro IS NULL;

UPDATE encore_show
SET
  intro = '科幻史诗续章，保罗·厄崔迪在沙丘世界中面对复仇、信仰与命运的抉择。',
  cast_members = '保罗·厄崔迪、契妮、杰西卡夫人、弗雷曼人、哈克南家族',
  creative_team = '导演：丹尼斯·维伦纽瓦；原著：弗兰克·赫伯特；放映版本：IMAX 特别版',
  full_synopsis = '保罗与母亲进入弗雷曼人的世界，在沙漠中学习生存、战斗与信仰。他与契妮的关系逐渐加深，同时也被预言和权力推向更危险的位置。面对家族仇恨、帝国政治和宗教狂热，保罗必须选择自己将成为怎样的领袖。'
WHERE id = 's-004' AND intro IS NULL;

UPDATE encore_show
SET
  intro = '星河回响演唱会采用混合票务模式，内场站席与看台座席共同组成沉浸式音乐现场。',
  cast_members = '星河回响乐队、特邀嘉宾、现场弦乐组、电子视觉团队',
  creative_team = '音乐制作：ENCORE Live Studio；舞美设计：Galaxy Stage Lab；灯光视觉：Aurora Crew',
  full_synopsis = '整场演唱会分为“启航”“穿越”“回声”“归来”四个篇章，从电子流行、摇滚段落到大型合唱逐步推进。观众可选择内场站立区域近距离参与，也可选择看台固定座席俯瞰全场灯海。'
WHERE id = 's-005' AND intro IS NULL;
