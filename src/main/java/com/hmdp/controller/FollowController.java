package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;
    @PutMapping("/{id}/{followedUserId}")
    public Result followUser(@PathVariable("id") Long id,@PathVariable("followedUserId") Boolean followedUserId){
        return followService.follow(id,followedUserId);
    }
@GetMapping("/or/not/{id}")
    public Result queryFollowings(@PathVariable("id") Long id){
        return followService.isfollow(id);
    }
    @GetMapping("/common/{id}")
    public Result querysameFollowings(@PathVariable("id") Long id){
        return followService.querysameFollow(id);
    }
}
