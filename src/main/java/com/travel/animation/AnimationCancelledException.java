package com.travel.animation;

/**
 * 用户取消或后台检测到取消标记后中止厂商轮询。
 */
public class AnimationCancelledException extends RuntimeException
{

    public AnimationCancelledException()
    {
        super("任务已取消");
    }
}
