package com.example.trippaminebe.security.auth;

import com.example.trippaminebe.domain.user.service.custom.CustomUserDetailsService;
import com.example.trippaminebe.security.jwt.JWTUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NegoStompAuthInterceptor implements ChannelInterceptor {
	private final JWTUtils jwtUtils;
	private final CustomUserDetailsService customUserDetailsService;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
			String token = accessor.getFirstNativeHeader("Authorization"); // "Bearer {token}"
			if (token != null && token.startsWith("Bearer ") && jwtUtils.validateToken(token.substring(7))) {
				String email = jwtUtils.getEmailFromToken(token.substring(7));
				UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
				accessor.setUser(new UsernamePasswordAuthenticationToken(
						userDetails, null, userDetails.getAuthorities()));
			} else {
				throw new IllegalArgumentException("WebSocket 인증 실패: 유효하지 않은 토큰입니다.");
			}
		}
		return message;
	}

}
