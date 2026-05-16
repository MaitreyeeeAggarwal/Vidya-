import { PrismaClient, PedagogyMode } from '@prisma/client';
import * as fs from 'fs';
import * as path from 'path';

const prisma = new PrismaClient();

async function main() {
  const seedPath = path.join(__dirname, 'ncert_seed.json');
  const rawData = fs.readFileSync(seedPath, 'utf-8');
  const data = JSON.parse(rawData);

  console.log('🔄 Cleaning curriculum tables...');
  await prisma.concept.deleteMany({});
  await prisma.chapter.deleteMany({});
  await prisma.subject.deleteMany({});
  await prisma.board.deleteMany({});

  console.log('🌱 Injecting curriculum static graph roots...');

  for (const boardData of data) {
    const board = await prisma.board.create({
      data: {
        name: boardData.board,
        state: boardData.state,
      },
    });

    for (const subjData of boardData.subjects) {
      const subject = await prisma.subject.create({
        data: {
          name: subjData.name,
          grade: subjData.grade,
          boardId: board.id,
        },
      });

      for (const chapData of subjData.chapters) {
        const chapter = await prisma.chapter.create({
          data: {
            chapterNum: chapData.chapterNum,
            title: chapData.title,
            subjectId: subject.id,
          },
        });

        for (const conceptData of chapData.concepts) {
          await prisma.concept.create({
            data: {
              code: conceptData.code,
              name: conceptData.name,
              pedagogyMode: conceptData.pedagogyMode as PedagogyMode,
              difficulty: conceptData.difficulty,
              chapterId: chapter.id,
            },
          });
        }
      }
    }
  }

  console.log('✅ Local Curriculum Graph Seed Completed Successfully.');
}

main()
  .catch((e) => {
    console.error('❌ Seeding failed: ', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
