//
//  OGWaveManager.m
//  OGReactNativeWaveform
//
//  Created by juan Jimenez on 10/01/2017.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import "RNZoomViewManager.h"
#import <React/UIView+React.h>
#import "RNZoomView.h"
#import "RNMeetingCenter.h"

@implementation RNZoomViewManager

RCT_EXPORT_VIEW_PROPERTY(userID, NSString);

- (UIView *)view
{
    RNZoomView *rnZoomView = [[RNZoomView alloc] init];
    return rnZoomView;
}
RCT_EXPORT_MODULE();

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

@end
