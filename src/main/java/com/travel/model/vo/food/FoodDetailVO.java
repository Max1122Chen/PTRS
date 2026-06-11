package com.travel.model.vo.food;

import com.travel.model.entity.Food;
import com.travel.model.vo.comment.CommentVO;

import java.util.List;

/**
 * 美食详情视图，补充餐厅与景区名称，避免前端展示技术 ID。
 */
public class FoodDetailVO
{

    private Food food;

    private String restaurantName;

    private String areaName;

    private List<CommentVO> comments;

    public Food getFood()
    {
        return food;
    }

    public void setFood(Food food)
    {
        this.food = food;
    }

    public String getRestaurantName()
    {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName)
    {
        this.restaurantName = restaurantName;
    }

    public String getAreaName()
    {
        return areaName;
    }

    public void setAreaName(String areaName)
    {
        this.areaName = areaName;
    }

    public List<CommentVO> getComments()
    {
        return comments;
    }

    public void setComments(List<CommentVO> comments)
    {
        this.comments = comments;
    }
}
