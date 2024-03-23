#include <francescozoccheddu_androidpp_MainActivity.h>
#include <random>

JNIEXPORT jint JNICALL Java_francescozoccheddu_androidpp_MainActivity_getRandomNumber(JNIEnv*, jobject)
{
  std::random_device rd;
  std::mt19937 gen(rd());
  std::uniform_int_distribution<> distr(1, 1000);
  return distr(gen);
}